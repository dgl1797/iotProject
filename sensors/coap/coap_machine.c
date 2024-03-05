#include "contiki.h"
#include "coap-engine.h"
#include "sys/etimer.h"
#include "sys/ctimer.h"
#include "net/routing/routing.h"
#include "net/ipv6/uip.h"
#include "net/ipv6/uip-icmp6.h"
#include "net/ipv6/sicslowpan.h"
#include "os/sys/log.h"
#include "os/dev/leds.h"
#include "lib/sensors.h"
#include "coap-blocking-api.h"
#include "dev/button-hal.h"

#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#define NODE_ID 2

/* LEDS */
#define LG 4
#define LR 2
#define LC 12
#define LB 8
#define LP 10

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "Machine Coap Server"
#define LOG_LEVEL LOG_LEVEL_APP

/*-----------------JSON HANDLING------------------------*/
char* next_pair(uint8_t* start_index, char* json){
  char *it = NULL;
  bool is_key = true;
  bool new_string = false;
  char* start = NULL;
  uint8_t index = 0;
  for (it = json+(*start_index); *it != '\0'; it++){
    if(!new_string){
        // control phase
        if(*it == '"'){
            new_string = true;
            if(is_key) start = it;
        }else if(*it == ':'){
            is_key = false;
        }else if(*it == ',' || *it == '}'){
            *it='\0';
            is_key = true;
            *start_index = index+1;
            return start;
        }
    } else if(*it=='"') new_string = false;
    index++;
  }
  return NULL;
}

char* extract_value(char* pair){
  char *start = NULL;
  char *it = NULL;
  bool is_value = false;
  bool new_string = false;
  for (it = pair; *it != '\0'; it++){
    if (!is_value && *it == ':') is_value = true;
    else if (!new_string && is_value && *it == '\"'){
      start = it+1;
      new_string = true;
    }else if (new_string && *it == '\"') *it = '\0';
  }
  return start;
}
/*-----------------JSON HANDLING END------------------------*/

/*-----------------------NODE PROCESS-----------------------*/
#define SERVER_EP "coap://[fd00::1]:5683"
#define SERVER_URI "/register"
#define SERVER_BURI "/mahbutton"
#define RETRY_PERIOD 2*CLOCK_SECOND

static bool registered = false;

extern coap_resource_t res_machine_switch,
                       res_machine_temperature;


void button_response_handler(coap_message_t *response){
  const uint8_t *chunk = NULL;
  int len = coap_get_payload(response, &chunk);
  if(response == NULL || len < 0){
    LOG_INFO("[COAP:MAH] - Error during mode switch, state remains invariate\n");
  }else{
    char *message = malloc((len+1) * sizeof(char));
    sprintf(message, "%.*s", len, (char *)chunk);
    message[len] = '\0';
    LOG_INFO("[COAP:MAH] - Message received: %s\n", message);
    if(strcmp("{\"res\":\"success\"}", message) == 0){
      LOG_INFO("[COAP:MAH:SUCCESS] - Mode Changed\n");
    } else{
      LOG_INFO("[COAP:MAH:FAIL] - Mode Unchanged\n");
    }

  }
}

void response_handler(coap_message_t *response)
{
  const uint8_t *chunk = NULL;

  if(response == NULL) {
    LOG_INFO("Request timed out\n");
    return;
  }

  int len = coap_get_payload(response, &chunk);
  if(len < 0){
    LOG_INFO("[COAP:MAH] - Payload problem\n");
    return;
  }
  char *message = malloc((len+1) * sizeof(char));
  message[len] = '\0';
  sprintf(message, "%.*s", len, (char *)chunk);
  LOG_INFO("[COAP:MAH] - Message received: %s\n", message);
  if(strcmp("{\"res\":\"success\"}", message) == 0){
    registered = true;
    LOG_INFO("[COAP:MAH:SUCCESS] - Registration completed\n");
  }
}

static bool is_reachable(){
  if(NETSTACK_ROUTING.node_is_reachable()) {
		LOG_INFO("[COAP:MAH] - BR reachable\n");
		return true;
  	}

	LOG_INFO("[COAP:MAH] - Waiting for connection with BR\n");
	return false;
}

PROCESS(machine_coap_node, "CoAP Machine");
AUTOSTART_PROCESSES(&machine_coap_node);

PROCESS_THREAD(machine_coap_node, ev, data)
{
  PROCESS_BEGIN();

  // VERIFY CONNECTION
  static struct etimer connectivity_timer;
  etimer_set(&connectivity_timer, RETRY_PERIOD*5);
  while(!is_reachable()){
    PROCESS_WAIT_UNTIL(etimer_expired(&connectivity_timer));
    etimer_reset(&connectivity_timer);
  }

  static coap_endpoint_t server_ep;
  static coap_message_t request;
  static struct etimer retry_timer;

  coap_endpoint_parse(SERVER_EP, strlen(SERVER_EP), &server_ep);

  char payload[50];
  sprintf(payload, "{\"id\":%d}", NODE_ID);
  while(!registered){
    LOG_INFO("[COAP:MAH] - Trying to register to CoAP\n");
    /* Prepare request */
    coap_init_message(&request, COAP_TYPE_CON, COAP_POST, 0);
    coap_set_header_uri_path(&request, SERVER_URI);
    coap_set_payload(&request, payload, strlen(payload));

    /* Send request */
    COAP_BLOCKING_REQUEST(&server_ep, &request, response_handler);
    etimer_set(&retry_timer, RETRY_PERIOD);
    PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&retry_timer));
  }

  // ACTIVATE RESOURCES
  coap_activate_resource(&res_machine_switch, "machine/switch_act");
  coap_activate_resource(&res_machine_temperature, "machine/temp_act");

  // KEEP LISTENING FOR BUTTON PRESSURE
  static uint8_t dt = 0;
  while(1){
    PROCESS_YIELD();
    if (ev == button_hal_press_event){
      dt = 0;
    }else if(ev == button_hal_periodic_event){
      dt++;
    }else if(ev == button_hal_release_event){
      if(dt >= 3){
        sprintf(payload, "{\"type\":\"reset\"}");
        coap_init_message(&request, COAP_TYPE_CON, COAP_POST, 0);
        coap_set_header_uri_path(&request, SERVER_BURI);
        coap_set_payload(&request, payload, strlen(payload));

        /* Send request */
        COAP_BLOCKING_REQUEST(&server_ep, &request, button_response_handler);
      }else{
        sprintf(payload, "{\"type\":\"mode_switch\"}");
        coap_init_message(&request, COAP_TYPE_CON, COAP_POST, 0);
        coap_set_header_uri_path(&request, SERVER_BURI);
        coap_set_payload(&request, payload, strlen(payload));

        /* Send request */
        COAP_BLOCKING_REQUEST(&server_ep, &request, button_response_handler);
      }
    }
  }

  PROCESS_END();
}
/*-----------------------NODE PROCESS END-----------------------*/