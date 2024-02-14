#include "contiki.h"
#include <stdio.h>

PROCESS(helloworld_process, "Hello World");
AUTOSTART_PROCESS(&helloworld_process);

PROCESS_THREAD(helloworld_process, ev, data){
  PROCESS_BEGIN();
  int c = 0;
  while (true){
    printf("Hello World!");
    PROCESS_WAIT();
  }
  
}