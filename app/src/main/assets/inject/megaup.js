
console.log = null;
console.warn = null;
console.error = null;
var _trueWindow=window;
_trueWindow.real_atob=_trueWindow.atob;
_trueWindow.real_btoa=_trueWindow.btoa;
_trueWindow.encodeURIComponentTmp=_trueWindow.encodeURIComponent;
_trueWindow.decodeURIComponentTmp=_trueWindow.decodeURIComponent;
_trueWindow.JSON.parseOri=_trueWindow.JSON.parse;
_trueWindow.decryptMega=null;

(function(jqw){
    function processMessage(id, data){
        if (_trueWindow.decryptMega){
            _trueWindow.decryptMega(data, function(res){
                parent.postMessage(JSON.stringify({
                        megaupcmd:"decryptresult",
                        megaupid:id,
                        data:res
                }),'*');
            });
        }
    }

    /* message handler */
    window.addEventListener('message',function(e) {
        try{
            console.info(["MEGAUP IFRAME MESSAGE",e]);
            var j=JSON.parse(e.data);
            if ('megaupcmd' in j){
                if (j.megaupcmd=="decrypt"){
                    processMessage(j.megaupid, j.data);
                }
            }
        }catch(e){}
    });

    function rollbackFn(){
        _trueWindow.atob=_trueWindow.real_atob;
        _trueWindow.btoa=_trueWindow.real_btoa;
        _trueWindow.decodeURIComponent=_trueWindow.decodeURIComponentTmp;
        _trueWindow.encodeURIComponent=_trueWindow.encodeURIComponentTmp;
        _trueWindow.JSON.parse=_trueWindow.JSON.parseOri;
    }
    var cbFn=null;
    function hackMega(cb){
        cbFn = cb;
        _trueWindow.decryptMega=function(txt, resCb){
            let resVal={
                status:200,
                result: txt
            };
            _trueWindow.JSON.parse=function(u){
                beforeJson=false;
                resCb(u);
                return null;
            };
            try{
                cb(resVal);
            }catch(ee){}
            if (resCb){
                resCb(null);
            }
            rollbackFn();
        };
    }
    if (location.host=="megaup.cc"){
        jqw.ajaxOri=jqw.ajax;
        jqw.ajax=function(u,o){
            if ((typeof u === 'string') && (u.startsWith("/media"))){
                let x = jqw.ajaxOri(u,o);
                x.realDone=x.done;
                x.done=function(res){
                    _trueWindow.__done=res;
                    hackMega(res);
                    return x.realDone(res);
                };
                return x;
            }
            return jqw.ajaxOri(u,o);
        };
        parent.postMessage(JSON.stringify({
            megaupcmd:"initial"
        }),'*');
        document.body.innerHTML='';
    }
})(_trueWindow.$);
