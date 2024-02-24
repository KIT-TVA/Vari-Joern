#include <iostream>
#include <cstring>

using namespace std;

extern "C" {
  void sayHello() {
    cout << "Hello world!" << endl;
    char *buf = (char*) malloc(12 + 1);
    if (!buf) return;
    memcpy(buf, "Hello world!", 13);
    free(buf);
  }
}

