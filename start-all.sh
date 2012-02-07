#!/bin/sh
php-cgi -b 127.0.0.1:6666 &
/usr/local/sbin/lighttpd -f ~/clojure/src/symbolicweb/lighttpd.conf
lein repl
