"use strict"; // http://ejohn.org/blog/ecmascript-5-strict-mode-json-and-more/



var sw_spinner = false;

function swPrepareSpinner(){
  sw_spinner = setTimeout(function(){ $("html, body").css("cursor", "wait"); },
                          100);
}

function swCancelSpinner(){
  if(sw_spinner){
    clearTimeout(sw_spinner);
    sw_spinner = false;
    $("html, body").css("cursor", "auto");
  }
}



/// HTML5 History shim for crappy browsers ///
//////////////////////////////////////////////

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



/// swHandleError ///
/////////////////////

function swHandleError(){
  try{
    if(console) // Not using shitty IE browser?
      console.error("swHandleError: " + arguments);
    swAjax("&do=error", {"msg": JSON.stringify(arguments, null, 2)});
  }
  catch(e){
    console.error("swHandleError: Total fail..");
    return(true); // Can't do anything reasonable here; don't let default handler run.
  }
  return(false); // Let default handler run.
}

TraceKit.report.subscribe(swHandleError);



/// swAddOnLoadFN ///
/////////////////////

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
         TraceKit.report(ex);
       }
     }
   };
 })();



/// swURL ///
/////////////

function swURL(params){
  return([window.location.protocol, "//",
          window.location.host,
          window.location.pathname,
          "?_sw_viewport_id=", _sw_viewport_id,
          params.join("")
         ].join(""));
}



/// swAjax ///
//////////////

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
            console.log("swAjax, fail: Trying again!");
            setTimeout(function(){ doIt(); }, 1000);
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



/// swComet ///
///////////////

var _sw_comet_response_p = false;
var _sw_comet_last_activity_ts = new Date().getTime();

var swComet  = (function(){

  function doIt(params){
    $.ajax({type: "POST",
            url: swURL(["&_sw_request_type=comet", params]),
            dataType: "script"})
      .always(function(){
        _sw_comet_last_activity_ts = new Date().getTime();
        if(_sw_comet_response_p){ // Got response from server?
          _sw_comet_response_p = false;
          swComet("&do=ack");
        }
        else{
          console.log("swComet: reboot!");
          setTimeout("swComet('&do=reboot');", 1000);
        }
      });
  }

  // Stops "throbbing of doom" and ensure we do not recurse until a stack overflow.
  return function(params){ setTimeout(function(){ doIt(params); }, 0); };
})();



/// swWidgetEvent ///
////////////////////

function swWidgetEvent(widget_id, callback_id, js_before, callback_data, js_after){
  if(js_before())
    swAjax("&do=widget-event" + "&_sw_widget-id=" + widget_id + "&_sw_callback-id=" + callback_id,
           callback_data,
           js_after());
}



/// swViewportEvent ///
///////////////////////

function swViewportEvent(callback_id, js_before, callback_data, js_after){
  if(js_before())
    swAjax("&do=viewport-event" + "&_sw_callback-id=" + callback_id,
           callback_data,
           js_after());
}



/// Shutdown ///
/////////////////

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



/// Boot! ///
/////////////

function swBoot(){
  if(document.cookie.indexOf(sw_cookie_name) != -1){
    // At this point pre-boot and all context (variables etc) is good to go so we connect our background channel.
    swComet("&do=boot");

    // Make sure things stay connected.
    setInterval(function(){
      var ts = new Date().getTime();
      if((ts - _sw_comet_last_activity_ts) // Time elapsed since last swComet activity...
         > (_sw_comet_timeout_ms + 5000)){ // ..with a 5 second latency window.
        console.log("SW: JS keep-alive reboot!");
        swComet("&do=reboot");
      }
    }, 1000);

  }
  else{
    console.error("SymbolicWeb: Cookies must be enabled.");
  }
}
