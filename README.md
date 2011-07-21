# SymbolicWeb

AJAX/Comet/ReverseHTTP/WebSockets/whatever Web UI (WUI) thing for Clojure.

SW was originally written for Common Lisp, and that version is still found at this location albeit unmaintained:

  https://github.com/lnostdal/old-SymbolicWeb



## Status

Very, very alpha -- and this doesn't do anything much useful yet.



## Usage

For PHP support if that's needed:

    php-cgi -b 127.0.0.1:6666 &


Lighttpd 1.5.x (from svn/git) is used for the boring static content. To enable serving from port 80 we do:

    sudo setcap 'cap_net_bind_service=+ep' /usr/local/sbin/lighttpd


..then we start it:

    /usr/local/sbin/lighttpd -f ~/clojure/src/symbolicweb/lighttpd.conf


Start swank in the SW directory:

    ~/clojure/src/symbolicweb$ lein swank


Start Emacs and connect to the now running swank using Slime, then make sure everything's loaded:

    user> (require 'symbolicweb.core)


Now start SW:

    user> (in-ns 'symbolicweb.core)
    symbolicweb.core> (-main)


Direct your browser to http://localhost.nostdal.org/sw/hello-world (yes, this will really resolve to 127.0.0.1) and it'll show a
welcome screen of sorts.

To send JS to the browser, try:

    symbolicweb.core> (add-response-chunk "alert('hi');" *viewport*)



## License

Copyright (C) 2011 Lars Rune Nøstdal

Distributed under the Affero General Public License (for now).


(.. I'm aiming at a 132 column width: http://ask.slashdot.org/story/07/07/07/1931246/Are-80-Columns-Enough ..)
