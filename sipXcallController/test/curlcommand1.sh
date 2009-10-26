#!/bin/sh
echo "Initiating a call between 201 and 202 as 201 from outside the sipx domain using HTTP post"
echo "You can call this from inside the sipx domain or outside. The password is not needed inside."
curl  -k -X POST -u user1:123  https://sipxtest.sipxtest.net:6666/callcontroller/user1/user2
sleep 20
echo "Query call setup progress for call between 201 and 202 as 201"
curl -k -u user1:123 https://sipxtest.sipxtest.net:6666/callcontroller/user1/user2
sleep 180
echo "Query call setup progress for call between 201 and 202 as 201 should give nothing ( record is gone ) "
curl -k -u user1:123 https://sipxtest.sipxtest.net:6666/callcontroller/user1/user2