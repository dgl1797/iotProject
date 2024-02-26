#include "contiki.h"
#include "sys/etimer.h"
#include "sys/ctimer.h"
#include "net/routing/routing.h"
#include "net/ipv6/uip.h"
#include "net/ipv6/uip-icmp6.h"
#include "net/ipv6/sicslowpan.h"
#include "os/sys/log.h"
#include "lib/sensors.h"
#include "mqtt.h"
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

/*----------------------PROCESS SETUP-----------------------------------*/
PROCESS(environment_sensor, "helloworld process");
AUTOSTART_PROCESSES(&environment_sensor);
/*----------------------PROCESS SETUP END-----------------------------------*/

/*-----------------------LOG CONFIG----------------------------------*/
#define LOG_MODULE "Env_Temperature_Logs"
#define LOG_LEVEL LOG_LEVEL_APP
/*-----------------------LOG CONFIG END----------------------------------*/

/*------------------------MQTT CONFIG-----------------------------------*/
#define MQTT_CLIENT_BROKER_IP_ADDR "fd00::1"

static const char *broker_ip = MQTT_CLIENT_BROKER_IP_ADDR;

// Default config values
#define DEFAULT_BROKER_PORT         1883
#define DEFAULT_PUBLISH_INTERVAL    (30 * CLOCK_SECOND)
/*------------------------MQTT CONFIG END-----------------------------------*/

/*------------------------STATES CONFIG----------------------------------*/
static uint8_t state;
static signed char temperature = 20; // starts from 20°C
static signed char humidity = 30; // starts from 30% humidity
static unsigned short int output = 0; // starts from 0 output produced

#define STATE_INIT    		    0
#define STATE_NET_OK    	    1
#define STATE_CONNECTING      2
#define STATE_CONNECTED       3
#define STATE_SUBSCRIBED      4
#define STATE_DISCONNECTED    5
/*------------------------STATES CONFIG END----------------------------------*/

/*------------------------BUFFERS SETUP-------------------------------------*/
#define MAX_TCP_SEGMENT_SIZE      32
#define CONFIG_IP_ADDR_STR_LEN    64
#define BUFFER_SIZE               64
#define MAX_CAPACITY              50
#define APP_BUFFER_SIZE           512

static char client_id[BUFFER_SIZE];
static char mdata_topic[BUFFER_SIZE];
static char app_buffer[APP_BUFFER_SIZE];
/*------------------------BUFFERS SETUP END-------------------------------------*/

/*------------------------TIMERS SETUP-----------------------------------*/
#define STATE_CHECK_PERIOD CLOCK_SECOND*5
#define PUBLICATION_PERIOD CLOCK_SECOND*15

static struct etimer state_check_timer;
static struct etimer publication_timer;
/*------------------------TIMERS SETUP END-----------------------------------*/

/*------------------------MQTT MESSAGES SETUP------------------------------*/
#define MQTT_TOPIC_NAME "mdata"
static struct mqtt_message *msg_ptr = 0;

static struct mqtt_connection conn;

mqtt_status_t status;
char broker_address[CONFIG_IP_ADDR_STR_LEN];
/*------------------------MQTT MESSAGES SETUP END------------------------------*/

/*------------------------MQTT EVENTS--------------------------------*/

static void pub_handler(const char *topic, uint16_t topic_len, const uint8_t *chunk,
            uint16_t chunk_len){
  
  // handler if the mqtt needs to do some action on publish

  /*printf("Pub Handler: topic='%s' (len=%u), chunk_len=%u\n", topic,
          topic_len, chunk_len);*/
/*
  if(strcmp(topic, "actuator") == 0) {
    printf("Received Actuator command\n");
	printf("%s\n", chunk);
    // Do something :)
    return;
  }*/
}

static void mqtt_event_handler(struct mqtt_connection *m, mqtt_event_t event, void *data){
  switch (event){
    case MQTT_EVENT_CONNECTED:
      LOG_INFO("[MAH:SUCCESS] - Mqtt Connection enstablished\n");
      state = STATE_CONNECTED;
      break;
    case MQTT_EVENT_DISCONNECTED:
      LOG_INFO("[MAH:FAIL] - MQTT Disconnect. Reason %u\n", *((mqtt_event_t *)data));
      state = STATE_DISCONNECTED;
      process_poll(&environment_sensor);
      break;
    case MQTT_EVENT_PUBLISH:
      msg_ptr = data;

      pub_handler(
        msg_ptr->topic, 
        strlen(msg_ptr->topic),
        msg_ptr->payload_chunk, 
        msg_ptr->payload_length
      );
      break;
    case MQTT_EVENT_SUBACK:
      LOG_INFO("[MAH:SUCCESS] - Subscription Succesful\n");
      break;
    case MQTT_EVENT_UNSUBACK:
      LOG_INFO("[MAH:SUCCESS] - Unsubbed from environment topic\n");
      break;
    case MQTT_EVENT_PUBACK:
      LOG_INFO("[MAH:SUCCESS] - Publication completed\n");
      break;

    default:
      LOG_INFO("[MAH:WARN] - Application got a unhandled MQTT event: %i\n", event);
      break;
  }
}
/*------------------------MQTT EVENTS END--------------------------------*/

/*------------------------UTILITY FUNCTIONS--------------------------------*/
static bool have_connectivity(void){
  if(uip_ds6_get_global(ADDR_PREFERRED) == NULL ||
     uip_ds6_defrt_choose() == NULL) {
    return false;
  }
  return true;
}

void clean_buffer(char *buffer){
  *buffer = '\0';
}
void set_data(char* buffer){
  srand(time(NULL));
  // random increment {0, 1, 2}
  signed char rand_tincrement = (signed char)((rand() % 3)+1);
  signed char rand_hincrement = ((signed char)(rand() % 11)) - ((signed char)5);
  unsigned short int rand_oincrement = (unsigned short int)(rand() % 31);

  // @TODO: check actuation state

  // temperature update
  temperature += rand_tincrement;
  humidity = temperature > 60 ? (10 + rand_hincrement) : 30 + rand_hincrement;
  output = rand_oincrement;

  // buffer update with JSON formatted string
  sprintf(buffer, "{\"temperature\":%d, \"humidity\":%d, \"outputs\":%d}", temperature, humidity, output);
}
/*------------------------UTILITY FUNCTIONS END--------------------------------*/


PROCESS_THREAD(environment_sensor, ev, data){
  PROCESS_BEGIN();
  LOG_INFO("[MAH:INFO] - Node started\n");
  // Initialize the ClientID as MAC address
  snprintf(client_id, BUFFER_SIZE, "%02x%02x%02x%02x%02x%02x",
                     linkaddr_node_addr.u8[0], linkaddr_node_addr.u8[1],
                     linkaddr_node_addr.u8[2], linkaddr_node_addr.u8[5],
                     linkaddr_node_addr.u8[6], linkaddr_node_addr.u8[7]);

  // Broker registration and state initialization
  mqtt_register(&conn, &environment_sensor, client_id, mqtt_event_handler, MAX_TCP_SEGMENT_SIZE);
  state=STATE_INIT;

  // Timers initialization
  etimer_set(&state_check_timer, STATE_CHECK_PERIOD);
  etimer_set(&publication_timer, PUBLICATION_PERIOD);
  
  // Main Loop
  while(1){
    PROCESS_YIELD();
    if((ev == PROCESS_EVENT_TIMER && data == &state_check_timer) || ev == PROCESS_EVENT_POLL ||
      (ev == PROCESS_EVENT_TIMER && data == &publication_timer)){
        
      if(state==STATE_INIT && have_connectivity()) state = STATE_NET_OK;

      if(state==STATE_NET_OK){
        LOG_INFO("[MAH:INFO] - Connecting\n");

        memcpy(broker_address, broker_ip, strlen(broker_ip));
        mqtt_connect(&conn, broker_address, DEFAULT_BROKER_PORT,
                    ( DEFAULT_PUBLISH_INTERVAL * 3) / CLOCK_SECOND,
                    MQTT_CLEAN_SESSION_ON);
        state = STATE_CONNECTING;
      }

      if(state == STATE_CONNECTED && etimer_expired(&publication_timer)){

        LOG_INFO("[MAH:INFO] - Publishing new message in %s topic\n", MQTT_TOPIC_NAME);

        sprintf(mdata_topic, "%s", MQTT_TOPIC_NAME);

			  clean_buffer(app_buffer);
        set_data(app_buffer);

        mqtt_publish(&conn, NULL, mdata_topic, (uint8_t *)app_buffer, strlen(app_buffer), 
          MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF
        );

		    // leds_on(LEDS_RED); ** TODO: Led di segnalazione se la temperatura è fuori dagli estremi

        etimer_set(&publication_timer, PUBLICATION_PERIOD);
      } else if (state == STATE_DISCONNECTED){
        LOG_INFO("[MAH:FAIL] - Lost connection to MQTT Broker\n");
        LOG_INFO("[MAH:INFO] - Reconnection Attempt...\n");
        mqtt_connect(&conn, broker_address, DEFAULT_BROKER_PORT,
          (DEFAULT_PUBLISH_INTERVAL * 3) / CLOCK_SECOND,
          MQTT_CLEAN_SESSION_ON
        );
        state = STATE_CONNECTED;
        etimer_reset(&publication_timer);
      }
    }
    etimer_reset(&state_check_timer);
  }
  PROCESS_END();
}