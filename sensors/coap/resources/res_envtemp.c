#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "sys/ctimer.h"

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "environment temperature"
#define LOG_LEVEL LOG_LEVEL_APP

#define GET_STATE_STRING(state_value) state_value == 0 ? "Off" : state_value == 1 ? "Heating" : "Cooling"
char *next_pair(uint8_t *start_index, char *json);
char *extract_value(char *pair);

/* LEDS */
#define LG 4
#define LR 2
#define LC 12
#define LB 8
#define LP 10

static uint8_t current_state = 0;

static void res_post_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

/* Temperature resource */
RESOURCE(res_environment_temperature,
         "title=\"environment temperature\";rt=\"envtemp\"",
         NULL,
         res_post_handler,
         NULL,
         NULL);

static void handle_state_change(uint8_t state){
  current_state = state;
  leds_off(leds_get());
  if(state == 1) leds_on(LP);
  else if (state == 2) leds_on(LB);
}

static void 
res_post_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset){
  // Check for valid request and response pointers
  LOG_INFO("[COAP:ENV:TEMP] - Received a request\n");
  if (!request || !response) {
      return;  // Handle error: invalid pointers
  }

  // Extract CoAP payload
  const uint8_t *chunk = NULL;
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
  uint8_t starting_index = 0;
  char *np = next_pair(&starting_index, payload);
  uint8_t commanded_state = atoi(extract_value(np));
  LOG_INFO("[COAP:ENV:TEMP] - Received command: %s\n", GET_STATE_STRING(commanded_state));
  handle_state_change(commanded_state);

  // Set appropriate response
  coap_set_status_code(response, CONTENT_2_05);
}