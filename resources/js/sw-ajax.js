"use strict"; // http://ejohn.org/blog/ecmascript-5-strict-mode-json-and-more/



// Deal with IE insanity ref. http://stackoverflow.com/a/13817235
(function(){
  if(!window.console){
    window.console = {};
  }
  var m = [
    "log", "info", "warn", "error", "debug", "trace", "dir", "group",
    "groupCollapsed", "groupEnd", "time", "timeEnd", "profile", "profileEnd",
    "dirxml", "assert", "count", "markTimeline", "timeStamp", "clear"
  ];
  for(var i = 0; i < m.length; i++){
    if(!window.console[m[i]]){
      window.console[m[i]] = function(){};
    }
  }
})();



console.log("###############################################################################\n" +
            "## Runs on the SymbolicWeb platform: https://github.com/lnostdal/SymbolicWeb ##\n" +
            "###############################################################################");



var sw_spinner_cnt = 0;
var sw_spinner = false;

function swPrepareSpinner(){
  if(sw_spinner_cnt == 0)
    sw_spinner = window.setTimeout(function(){ $("html, body").css("cursor", "wait"); },
                                   100);
  sw_spinner_cnt++;
}

function swCancelSpinner(){
  if(sw_spinner_cnt > 0){
    sw_spinner_cnt--;
    if(sw_spinner_cnt == 0){
      window.clearTimeout(sw_spinner);
      sw_spinner = false;
      $("html, body").css("cursor", "auto");
    }
  }
}



// HTML5 History shim for crappy browsers
if(!window.history.replaceState) {
  window.history.replaceState = function(x, y, url_search){
    if(url_search != window.location.search){
      window.location.replace("//" + window.location.host + window.location.pathname + url_search);
    }
  };
}
if(!window.history.pushState){
  window.history.pushState = function(x, y, url_search){
    if(url_search != window.location.search){
      window.location.assign("//" + window.location.host + window.location.pathname + url_search);
    }
  };
}



function swHandleError(){
  try{
    swAjax("&do=error", {"msg": JSON.stringify(arguments, null, 2)});
  }
  catch(e){
    console.error("swHandleError: Total fail..");
    return(true); // Can't do anything reasonable here; don't let default handler run.
  }
  return(false); // Let default handler run.
}

window.onerror = swHandleError;



var swAddOnLoadFN, swDoOnLoadFNs;
(function (){
  var funs = new Array();

  swAddOnLoadFN = function(fun){
    funs.push(fun);
  };

  swDoOnLoadFNs = function(){
    for(var fun in funs){
      try {
        funs[fun]();
      }
      catch(ex){
        swHandleError(ex);
      }
    }
  };
})();



function swURL(params){
  return([window.location.protocol, "//",
          window.location.host,
          window.location.pathname,
          "?_sw_viewport_id=", _sw_viewport_id,
          params.join("")
         ].join(""));
}



var swAjax = (function(){
  var queue = new Array();

  function handleRestOfQueue(){
    queue.shift();
    if(queue.length != 0)
      queue[0]();
  }

  return function(params, callback_data, after_fn){
    if(queue.push(function(){
      function doIt(){
        $.ajax({
          type: "POST",
          url: swURL(["&_sw_request_type=ajax", params]),
          data: callback_data,
          dataType: "script",
          beforeSend: swPrepareSpinner
        })
          .fail(function(jq_xhr, text_status, error_thrown){
            switch(jq_xhr.status){
            case 500:
              console.log("swAjax, server exception: Reloading page!");
              console.log(jq_xhr.responseText);
              window.location.href = window.location.href;
              break;
            default:
              console.log("swAjax, other failure (network?): " + error_thrown + ", " + text_status + ". Trying again!");
              if(error_thrown == "timeout"){
                doIt();
              } else {
                window.setTimeout(function(){ doIt(); }, 500);
              }
            }
          })
          .done(function(){
            if(after_fn) after_fn();
            queue.shift();
            if(queue.length != 0) queue[0]();
          });
      };

      doIt();

    }) == 1) // if()..
      queue[0]();
  };
})();



var _sw_comet_response_p = false;

var swComet  = (function(){

  function doIt(params){
    $.ajax({type: "POST",
            url: swURL(["&_sw_request_type=comet", params]),
            dataType: "script"})
      .always(function(){
        if(_sw_comet_response_p){ // Got response from server?
          _sw_comet_response_p = false;
          swComet("&do=ack");
        }
        else{
          console.log("swComet: reboot!");
          window.setTimeout("swComet('&do=reboot');", 1000);
        }
      });
  }

  // Stops "throbbing of doom" and ensure we do not recurse until a stack overflow.
  return function(params){ window.setTimeout(function(){ doIt(params); }, 0); };
})();



function swWidgetEvent(widget_id, callback_id, js_before, callback_data, js_after){
  if(js_before())
    swAjax("&do=widget-event" + "&_sw_widget-id=" + widget_id + "&_sw_callback-id=" + callback_id,
           callback_data,
           js_after());
}



function swViewportEvent(callback_id, js_before, callback_data, js_after){
  if(js_before())
    swAjax("&do=viewport-event" + "&_sw_callback-id=" + callback_id,
           callback_data,
           js_after());
}



// So unload event fires on (some, but still not all ..sigh) navigation in Opera too.
if(typeof(opera) != "undefined"){
  opera.setOverrideHistoryNavigationMode("compatible");
  history.navigationMode = "compatible";
}

// Eager, instead of based on timeout -- GC of the server side Viewport object and all its Widgets (and their incomming
// connections from the Model/DB) on page unload.
$(window).on("unload", function(){
  if(typeof(_sw_viewport_id) != "undefined")
    $.ajax({type: "POST",
            url: swURL(["&_sw_request_type=ajax", ["&do=unload"]]),
            async: false});
});



function swBoot(){
  if(document.cookie.indexOf(sw_cookie_name) != -1){
    // At this point pre-boot and all context (variables etc) is good to go so we connect our background channel.
    swComet("&do=boot");
  }
  else{
    console.error("Cookies must be enabled.");
  }
}
