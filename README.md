# SymbolicWeb

AJAX/Comet/reverse HTTP thing for Clojure.



## Status

Very, very alpha.



## Usage

For PHP support if that's needed:

    php-cgi -b 127.0.0.1:6666 &


Lighttpd 1.5.x (from svn/git) is used for the boring static content.
To enable serving from port 80 we do:

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


Direct your browser to http://localhost.nostdal.org/sw/ (yes, this will really resolve to 127.0.0.1) and
it'll show a welcome screen of sorts.

To send JS to the browser, try:

    symbolicweb.core> (add-response-chunk "alert('hi');" @*viewport*)



## License

Copyright (C) 2011 Lars Rune Nøstdal

Distributed under the Affero General Public License (for now).
