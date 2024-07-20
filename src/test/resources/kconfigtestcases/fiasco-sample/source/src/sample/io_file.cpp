INTERFACE:

IMPLEMENTATION:

#include <sys/stat.h>
#include <stdio.h>
#include "globalconfig.h"

void insecure_race(char *path) {
#ifdef CONFIG_PERFORM_CHMOD
    chmod(path, 0);
#endif
#ifdef CONFIG_PERFORM_RENAME
    rename(path, "/some/new/path");
#endif
}
