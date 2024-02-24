#include <stdio.h>
#include "main.h"

#ifdef CONFIG_USE_CPP_FILE
#include "hello-cpp.h"
#endif

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
