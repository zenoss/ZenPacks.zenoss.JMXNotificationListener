##############################################################################
#
# Copyright (C) Zenoss, Inc. 2008, 2012, all rights reserved.
#
# This content is made available according to terms specified in
# License.zenoss under the directory where your Zenoss product is installed.
#
##############################################################################

PYTHON=python
SRC_DIR=$(PWD)/src
JMXNL_DIR=$(SRC_DIR)/zenoss-jmx-notification-listener
ZENPACK_DIR=$(PWD)/ZenPacks/zenoss/JMXNotificationListener
BIN_DIR=$(ZENPACK_DIR)/bin
LIB_DIR=$(ZENPACK_DIR)/lib


# Default target. This won't be used by any automated process, but would be
# used if you simply ran "make" in this directory.
default: build


# The build target it specifically executed each time setup.py executes.
# Typically this is when the ZenPack is being built into an egg, or when it is
# installed using the zenpack --link option to install in development mode.
build:
	# Build zenoss-jmx-notification-listener.
	cd $(JMXNL_DIR) ; \
	mvn package ; \
	mkdir -p $(LIB_DIR) ; \
	cp target/zenoss-jmx-notification-listener-*.jar $(LIB_DIR)

	# Copy configuration files into ZenPack's lib directory.
	cp $(JMXNL_DIR)/src/main/resources/* $(LIB_DIR)


# The clean target won't be used by any automated process.
clean:
	# Cleanup Java stuff.
	rm -rf $(SRC_DIR)/target
	rm -rf $(LIB_DIR)

	# Cleanup Python stuff.
	rm -rf build dist *.egg-info
	find . -name '*.pyc' | xargs rm
