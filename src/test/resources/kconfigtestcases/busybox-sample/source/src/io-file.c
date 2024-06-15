//config:config INCLUDE_IO_FILE
//config:   bool "Include a file that contains a method performing I/O operations"
//config:   default true
//config:config PERFORM_CHMOD
//config:   bool "Run chmod on a path"
//config:   default false
//config:   depends INCLUDE_IO_FILE
//config:config PERFORM_RENAME
//config:   bool "Rename a path"
//config:   default false
//config:   depends INCLUDE_IO_FILE

//kbuild:obj-$(CONFIG_INCLUDE_IO_FILE) += io-file.o

#include <sys/stat.h>
#include <stdio.h>
#include "io-file.h"
#include "included.c"

void insecure_race(char *path) {
#ifdef CONFIG_PERFORM_CHMOD
    chmod(path, 0);
#endif
#ifdef CONFIG_PERFORM_RENAME
    rename(path, "/some/new/path");
#endif
}

