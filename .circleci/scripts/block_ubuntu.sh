#!/usr/bin/env bash


# repeatedly block and unblock one of the TCP port
# used by node 

set -eE



trap ctrl_c INT

function ctrl_c() {
    echo "** Trapped CTRL-C"
    echo "recover firewall rules"
    sudo iptables --flush
    exit
}

which tc >> exec.log

tc >> exec.log

while true; do 
    echo "Block port" >> exec.log
    sudo iptables -A INPUT -p tcp --destination-port 50204 -j DROP
    sleep 12
    echo "Enable port" >> exec.log
    sudo iptables --flush
    sleep 12
done

