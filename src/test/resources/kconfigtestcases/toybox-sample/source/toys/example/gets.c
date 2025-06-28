/*
USE_GETS(NEWTOY(gets, 0, TOYFLAG_USR|TOYFLAG_BIN))

config GETS
  bool gets
  default n
  help
    usage: gets

    A simple program that reads a line from stdin and prints it to stdout.

config USE_GETS
  bool "Use gets()"
  default n
  depends on GETS
  help
    usage: gets

    Use the gets() function to read input from stdin.
*/

#define FOR_gets
#include "toys.h"

GLOBALS(
  int unused;
)

// Make sure we don't get a compiler warning.
char* gets(char* str);

void gets_main(void)
{
        char buf[25];
#ifdef CFG_USE_GETS
        printf("Using gets()!");
        gets(buf);
#else
        fgets(buf, BUF_SIZE, stdin);
#endif
        xprintf("Text: %s", buf);
  // Avoid kernel panic if run as init.
  if (getpid() == 1) getchar();
}
