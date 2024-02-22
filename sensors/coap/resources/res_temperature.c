#include "contiki.h"
#include "coap-engine.h"
#include "random.h"

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "Temperature Resource"
#define LOG_LEVEL LOG_LEVEL_RESOURCE

static void res_get_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

/* Temperature resource */
RESOURCE(res_temperature,
         "title=\"Temperature sensor\";rt=\"Temperature Sensor\"",
         res_get_handler,
         NULL,
         NULL,
         NULL);

static void res_get_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset)
{
  /* Simulate temperature sensor reading */
  int temperature = (int)random_rand() % 50;

  unsigned int accept = -1;
  coap_get_header_accept(request, &accept);

  if(accept == -1 || accept == TEXT_PLAIN) {
    coap_set_header_content_format(response, TEXT_PLAIN);
    snprintf((char *)buffer, COAP_MAX_CHUNK_SIZE, "%d", temperature);

    coap_set_payload(response, (uint8_t *)buffer, strlen((char *)buffer));
  } else {
    coap_set_status_code(response, NOT_ACCEPTABLE_4_06);
  }
}