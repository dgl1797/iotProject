#include "contiki.h"
#include "coap-engine.h"
#include "sys/etimer.h"
#include "coap-blocking-api.h"

#define NODE_ID 2

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "App"
#define LOG_LEVEL LOG_LEVEL_APP

PROCESS(machine_coap_node, "CoAP Machine");
AUTOSTART_PROCESSES(&machine_coap_node);


#define SERVER_EP "coap://[fd00::1]:5683"
#define SERVER_URI "/register"

void response_handler(coap_message_t *response)
{
  const uint8_t *chunk;

  if(response == NULL) {
    LOG_INFO("Request timed out\n");
    return;
  }

  int len = coap_get_payload(response, &chunk);
  LOG_INFO("|%.*s\n", len, (char *)chunk);
}

PROCESS_THREAD(machine_coap_node, ev, data)
{
  PROCESS_BEGIN();

  static coap_endpoint_t server_ep;
  static coap_message_t request;

  LOG_INFO("Starting Machine CoAP Node\n");

  coap_endpoint_parse(SERVER_EP, strlen(SERVER_EP), &server_ep);

  char payload[50];
  sprintf(payload, "{\"id\":%d}", NODE_ID);
  /* Prepare request */
  coap_init_message(&request, COAP_TYPE_CON, COAP_POST, 0);
  coap_set_header_uri_path(&request, SERVER_URI);
  coap_set_payload(&request, payload, strlen(payload));

  /* Send request */
  COAP_BLOCKING_REQUEST(&server_ep, &request, response_handler);

  PROCESS_END();
}