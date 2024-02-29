#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "sys/ctimer.h"

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "light controller"
#define LOG_LEVEL LOG_LEVEL_APP

#define GET_STATE_STRING(state_value) state_value == 0 ? "Off" : state_value == 1 ? "Heating" : "Cooling"

static void res_post_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

/* Temperature resource */
RESOURCE(res_environment_temperature,
         "title=\"environment temperature\";rt=\"envtemp\"",
         NULL,
         res_post_handler,
         NULL,
         NULL);

static void 
res_post_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset){
  // Check for valid request and response pointers
  LOG_INFO("[COAP:ENV:TEMP] - Received a request\n");
  if (!request || !response) {
      return;  // Handle error: invalid pointers
  }

  // Extract CoAP payload
  const uint8_t *chunk;
  uint16_t payload_len = coap_get_payload(request, &chunk);

  if(payload_len < 0){
    LOG_INFO("[COAP:ENV:TEMP] - Payload problem\n");
    coap_set_status_code(response, BAD_REQUEST_4_00);
    return;
  }
  char *payload = malloc((payload_len+1) * sizeof(char));
  payload[payload_len] = '\0';
  sprintf(payload, "%.*s", payload_len, (char *)chunk);

  // Process the extracted payload here
  LOG_INFO("[COAP:ENV:TEMP] - Received command: %s\n", payload);

  // Set appropriate response
  coap_set_status_code(response, CONTENT_2_05);
}