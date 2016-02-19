#!/bin/sh

# Install ZeroMQ
wget http://download.zeromq.org/zeromq-4.1.4.tar.gz
tar -xzf zeromq-4.1.4.tar.gz
cd zeromq-4.1.4
./configure
make
sudo make install
cd ../

# Install JZMQ
git clone https://github.com/zeromq/jzmq.git
cd jzmq
./autogen.sh
./configure
make
sudo make install
cd ../
