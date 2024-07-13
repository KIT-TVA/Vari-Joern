INTERFACE:

#define BUF_SIZE 25

int main(void);

IMPLEMENTATION:

#include "globalconfig.h"

#include <stdio.h>

int gets(char *buf);

int main(void) {
        char buf[BUF_SIZE];
#ifdef CONFIG_USE_GETS
        printf("Using gets()!");
        gets(buf);
#else
        fgets(buf, BUF_SIZE, stdin);
#endif
        printf("Text: %s", buf);
#ifdef CONFIG_USE_CPP_FILE
	sayHello();
#endif
}
