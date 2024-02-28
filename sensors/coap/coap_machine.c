#include "contiki.h"
#include "coap-engine.h"
#include "sys/etimer.h"
#include "sys/ctimer.h"
#include "net/routing/routing.h"
#include "net/ipv6/uip.h"
#include "net/ipv6/uip-icmp6.h"
#include "net/ipv6/sicslowpan.h"
#include "os/sys/log.h"
#include "lib/sensors.h"
#include "coap-blocking-api.h"
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#define NODE_ID 2

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "App"
#define LOG_LEVEL LOG_LEVEL_APP

PROCESS(machine_coap_node, "CoAP Machine");
PROCESS(machine_coap_resources, "CoAP Resources");
AUTOSTART_PROCESSES(&machine_coap_node, &machine_coap_resources);


/*-----------------------REGISTRATION PROCESS-----------------------*/
#define SERVER_EP "coap://[fd00::1]:5683"
#define SERVER_URI "/register"
#define RETRY_PERIOD 2*CLOCK_SECOND

static bool registered = false;

void response_handler(coap_message_t *response)
{
  const uint8_t *chunk;

  if(response == NULL) {
    LOG_INFO("Request timed out\n");
    return;
  }

  int len = coap_get_payload(response, &chunk);
  if(strcmp("{\"res\":\"success\"}", (char*)chunk) == 0){
    registered = true;
    LOG_INFO("[COAP:ENV:SUCCESS] - Registration completed");
  }
}

PROCESS_THREAD(machine_coap_node, ev, data)
{
  PROCESS_BEGIN();

  static coap_endpoint_t server_ep;
  static coap_message_t request;
  static struct etimer retry_timer;

  coap_endpoint_parse(SERVER_EP, strlen(SERVER_EP), &server_ep);

  char payload[50];
  sprintf(payload, "{\"id\":%d}", NODE_ID);
  etimer_set(&retry_timer, RETRY_PERIOD);
  while(!registered){
    LOG_INFO("[COAP:MAH] - Trying to register to CoAP");
    /* Prepare request */
    coap_init_message(&request, COAP_TYPE_CON, COAP_POST, 0);
    coap_set_header_uri_path(&request, SERVER_URI);
    coap_set_payload(&request, payload, strlen(payload));

    /* Send request */
    COAP_BLOCKING_REQUEST(&server_ep, &request, response_handler);
    PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&retry_timer));
    etimer_reset(&retry_timer);
  }

  PROCESS_END();
}
/*-----------------------REGISTRATION PROCESS END-----------------------*/

/*-----------------------RESOURCES PROCESS-------------------------*/
PROCESS_THREAD(machine_coap_resources, ev, data){
  PROCESS_BEGIN();
  LOG_INFO("[MAH:RES] - Hello");
  PROCESS_END();
}
/*-----------------------RESOURCES PROCESS END-------------------------*/