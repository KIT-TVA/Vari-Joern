#!/bin/bash

# Prerequisites:
# - Git is installed
# - A JDK >= 8 is installed
# - JAVA_HOME points to the JDK is exported in the shell used to execute this script (e.g., via export in ~/.bashrc).

# Usage: bash /path/to/vari-joern/scripts/install_superc.bash [installation_path]
# installation_path is optional. By default, superc will be installed to the user's home directory.

RED='\033[0;31m'
NC='\033[0m'

# Check whether JAVA_HOME is defined.
if [ -z "$JAVA_HOME" ]; then
  echo -e "${RED}Error: JAVA_HOME is undefined but is required to point to a JDK >= 8.${NC}"
  exit 1
fi

# Check whether JAVA_HOME points to a valid JDK.
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo -e "${RED}Error: JAVA_HOME does not point to a valid JDK.${NC}"
  exit 1
fi

JAVA_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "JAVA_HOME points to a JDK of version $JAVA_VERSION"

# Extract Java version number.
MAJOR_VERSION=$(echo "$JAVA_VERSION" | awk -F '.' '{print $1}') # (e.g., 1 in 1.8.0_251)
MINOR_VERSION=$(echo "$JAVA_VERSION" | awk -F '.' '{print $2}') # (e.g., 8 in 1.9.0_251)

# Check if the Java version is 8 or higher.
if [ "$MAJOR_VERSION" -eq 1 ] && [ "$MINOR_VERSION" -lt 8 ]; then
    echo -e "${RED}Error: JAVA_HOME points to a JDK < 8.${NC}"
    exit 1
fi

install_path=$HOME
script_path=$(readlink -f "$0")
script_dir=$(dirname "$script_path")

# Install dependencies.
echo "Installing dependencies of SuperC..."
sudo apt-get install bison libz3-java=4.8.7-4build1 libjson-java sat4j
echo "Done installing dependencies."

# Clone SuperC.
echo "Cloning the SuperC repo..."

if [ -n "$1" ]; then
    install_path=$1
fi

if [ -d "$install_path/superc" ]; then
  echo "Removing existing SuperC directory found in install path '$install_path'."
  rm -rf "$install_path/superc"
fi

echo "Executing clone from within $install_path"
cd "$install_path" || exit
git clone https://github.com/KIT-TVA/superc.git
echo "Done cloning SuperC."

cd superc || exit

# Set environment variables.
echo "Setting up environment variables..."

echo "Backing up .bashrc to .bashrc.bak."
cp ~/.bashrc ~/.bashrc.bak

# Add variables to .bashrc.
echo "# SuperC environment variables set by Vari-Joern's install_superc script." >> ~/.bashrc

# JAVA_DEV_ROOT.
JAVA_DEV_ROOT="$(pwd)"
export JAVA_DEV_ROOT="$JAVA_DEV_ROOT"
if ! grep -q "^export.*JAVA_DEV_ROOT" ~/.bashrc; then
  echo "export JAVA_DEV_ROOT=$JAVA_DEV_ROOT" >> ~/.bashrc
else
  echo -e "${RED} Warning: JAVA_DEV_ROOT was not written to .bashrc since it appears to be already defined.${NC}"
fi

# CLASSPATH.
CLASSPATH_RESOLVED="$CLASSPATH:"\
"$JAVA_DEV_ROOT/classes:"\
"$JAVA_DEV_ROOT/bin/junit.jar:"\
"$JAVA_DEV_ROOT/bin/antlr.jar:"\
"$JAVA_DEV_ROOT/bin/javabdd.jar:"\
"$JAVA_DEV_ROOT/bin/json-simple-1.1.1.jar:"\
"/usr/share/java/org.sat4j.core.jar:"\
"/usr/share/java/com.microsoft.z3.jar:"\
"/usr/share/java/json-lib.jar"

CLASSPATH_UNRESOLVED='$CLASSPATH:'\
'$JAVA_DEV_ROOT/classes:'\
'$JAVA_DEV_ROOT/bin/junit.jar:'\
'$JAVA_DEV_ROOT/bin/antlr.jar:'\
'$JAVA_DEV_ROOT/bin/javabdd.jar:'\
'$JAVA_DEV_ROOT/bin/json-simple-1.1.1.jar:'\
'/usr/share/java/org.sat4j.core.jar:'\
'/usr/share/java/com.microsoft.z3.jar:'\
'/usr/share/java/json-lib.jar'

export CLASSPATH="$CLASSPATH_RESOLVED"
echo "export CLASSPATH=$CLASSPATH_UNRESOLVED"  >> ~/.bashrc

echo "Done setting up environment variables."

# Build SuperC.
echo "Building SuperC from within '$(pwd)'"

if ! make configure; then
  echo -e "${RED}Hint: make configure failed.${NC}"
  exit 1
fi
if ! make; then
  echo -e "${RED}Hint: make failed.${NC}"
    exit 1
fi

echo "Done building SuperC."

# Copy jars into Vari-Joern's lib directory,
echo "Copying necessary jars from SuperC to lib directory..."
cp "$install_path/superc/bin/xtc.jar" "$install_path/superc/bin/superc.jar"  "$script_dir/../lib/"
echo "Done copying necessary jars."

echo -e "${RED}Hint: Please run 'source ~/.bashrc' or restart open terminals to apply changes to the environment variables.${NC}"
echo -e "${RED}Hint: For correct operation of SuperC, make sure that JAVA_HOME remains pointing to a JDK >= Java 8 "\
"(currently points to $JAVA_HOME).${NC}"