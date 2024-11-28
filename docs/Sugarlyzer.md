
# Subject Systems supported by Sugarlyzer

Below, you find a list of the subject systems currently supported by Sugarlyzer.
The list also contains remarks on required libraries for the analysis of individual subject systems (only relevant for
native execution).

## AxTLS

### General Information:
- **Version**: 2.1.5 (March 2019)
- **Category**: SSL Client/Server Library
- **C Lines of Code** (as reported by Cloc): > 17,000

## Toybox

### General Information:
- **Version**: 0.8.11 (April 2024)
- **Category**: Linux Command Line Utilities
- **C Lines of Code** (as reported by Cloc): > 58.000

### Remarks:
- Certain source files of Toybox rely on headers of **OpenSSL** (notably ``toys/net/wget.c``, ``toys/pending/git.c``, and ``lib/hash.c``).
  - Either the development package for OpenSSL `libssl-dev` has to be installed (e.g., via sudo apt install libssl-dev), or 
  - For desugaring, usage of OpenSSL can be toggled off by predefining ``TOYBOX_LIBCRYPTO`` to undef in Toybox' program specification. However, this only avoids errors for files that conditionally include OpenSSL headers (i.e., ``toys/net/wget.c`` and ``lib/hash.c``).  

## BusyBox

### General Information:
- **Version**: 1.36.1
- **Category**: Collection of UNIX utilities
- **C Lines of Code** (as reported by Cloc): > 182,000

### Remarks
- Requires the SELinux development headers to be installed for executing make
  - Can be achieved by ``sudo apt-get install selinux-basics selinux-utils libselinux*``
