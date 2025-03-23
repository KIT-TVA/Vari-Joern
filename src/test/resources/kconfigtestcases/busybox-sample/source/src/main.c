#include <stdio.h>
#include "main.h"

#ifdef CONFIG_USE_CPP_FILE
#include "hello-cpp.h"
#endif

#include "included.c"

// Make sure we don't get a compiler warning. The WERROR option would otherwise fail the build.
char* gets(char* str);

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

#ifdef TEST
    printf("TEST is set");
#endif
#ifdef CONFIG_TEST
    printf("Feature TEST is enabled");
#endif
#if ENABLE_TEST
    printf("Feature TEST is still enabled");
#endif
}

// This comment includes special characters to verify that the encoding is respected: äöüß
