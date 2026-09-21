package com.opus.music.party

import fi.iki.elonen.NanoHTTPD

/**
 * Tiny embedded web server for Party Queue. Guests open the host's URL in
 * any browser — no app install needed — and get a search + queue + voting
 * page that talks to these JSON endpoints.
 */
class PartyServer(port: Int, private val session: PartySession) : NanoHTTPD(port) {

    override fun serve(http: IHTTPSession): Response {
        return try {
            val uri = http.uri.trimEnd('/')
            val ip = http.remoteIpAddress ?: "unknown"
            when (uri) {
                "" -> html(PAGE)
                "/api/state" -> json(stateJson(ip))
                "/api/search" -> {
                    val q = http.parameters["q"]?.firstOrNull().orEmpty()
                    json(searchJson(session.search(q, ip)))
                }
                "/api/add" -> {
                    val id = http.parameters["id"]?.firstOrNull().orEmpty()
                    val res = session.addSong(id, ip)
                    json("""{"ok":${res == "ok"},"reason":"$res","remaining":${session.policy.addsRemaining(ip)}}""")
                }
                "/api/vote" -> {
                    val id = http.parameters["id"]?.firstOrNull().orEmpty()
                    val (voted, n) = session.vote(id, ip)
                    json("""{"ok":true,"voted":$voted,"votes":$n}""")
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "error")
        }
    }

    private fun json(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", body)

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html", body)

    private fun esc(s: String?): String =
        (s ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", " ").replace("\r", " ")

    private fun stateJson(ip: String): String {
        val np = session.nowPlaying()
        val q = session.queueFor(ip)
        val sb = StringBuilder()
        sb.append("{")
        if (np != null) {
            sb.append("\"nowPlaying\":{\"id\":\"${esc(np.id)}\",\"title\":\"${esc(np.title)}\",")
            sb.append("\"artist\":\"${esc(np.artist)}\"},")
        } else {
            sb.append("\"nowPlaying\":null,")
        }
        sb.append("\"queue\":[")
        q.forEachIndexed { i, e ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${esc(e.id)}\",\"title\":\"${esc(e.title)}\",")
            sb.append("\"artist\":\"${esc(e.artist)}\",\"votes\":${e.votes},")
            sb.append("\"voted\":${e.votedByMe},\"mine\":${e.addedByMe}}")
        }
        sb.append("],")
        sb.append("\"addsRemaining\":${session.policy.addsRemaining(ip)},")
        sb.append("\"voting\":${session.policy.votingEnabled},")
        sb.append("\"guests\":${session.guestCount()}")
        sb.append("}")
        return sb.toString()
    }

    private fun searchJson(songs: List<com.opus.music.network.Song>): String {
        val sb = StringBuilder("{\"results\":[")
        songs.forEachIndexed { i, s ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${esc(s.id)}\",\"title\":\"${esc(s.title)}\",")
            sb.append("\"artist\":\"${esc(s.artist)}\",\"album\":\"${esc(s.album)}\"}")
        }
        return sb.append("]}").toString()
    }

    companion object {
        // NOTE: keep this page self-contained (no external requests).
        private const val PAGE = """
<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Opus Party Queue</title>
<style>
body{background:#0d0d12;color:#f2f2f5;font-family:system-ui,sans-serif;margin:0;padding:16px}
h1{font-size:20px;margin:4px 0 2px} .sub{color:#9a9aa5;font-size:13px;margin-bottom:14px}
.now{background:#1a1a22;border-radius:12px;padding:12px;margin-bottom:14px}
.now .t{font-weight:700} .now .a{color:#9a9aa5;font-size:13px}
input{width:100%;box-sizing:border-box;background:#1a1a22;border:1px solid #2c2c38;color:#fff;
 border-radius:10px;padding:12px;font-size:16px;margin-bottom:10px}
.row{display:flex;align-items:center;gap:10px;background:#14141b;border-radius:10px;
 padding:10px 12px;margin-bottom:8px}
.row .meta{flex:1;min-width:0}.row .ti{font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.row .ar{color:#9a9aa5;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
button{background:#7c5cff;border:0;color:#fff;border-radius:8px;padding:9px 14px;font-size:14px;font-weight:700}
button.ghost{background:#26262f} button.voted{background:#2f9e6e}
.sec{font-size:12px;text-transform:uppercase;letter-spacing:1px;color:#9a9aa5;margin:16px 0 8px}
.votes{color:#c9b8ff;font-weight:700;font-size:13px;min-width:44px;text-align:center}
.hint{color:#9a9aa5;font-size:12px;text-align:center;margin-top:18px}
</style></head><body>
<h1>&#127926; Opus Party Queue</h1>
<div class="sub" id="status">connecting&hellip;</div>
<div class="now" id="now"><div class="t">Nothing playing</div></div>
<input id="q" type="search" placeholder="Search songs to add&hellip;" autocomplete="off">
<div id="results"></div>
<div class="sec">Up next</div>
<div id="queue"></div>
<div class="hint">You can add <b id="left"></b> more songs. Tap &#128077; to vote a song up.</div>
<script>
const R=(id)=>document.getElementById(id);
async function api(p){const r=await fetch(p);return r.json();}
function esc(s){return (s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;');}
async function refresh(){
  try{
    const s=await api('/api/state');
    R('status').textContent=s.guests+' guest'+(s.guests==1?'':'s')+' connected';
    R('now').innerHTML=s.nowPlaying
      ? '<div class="t">'+esc(s.nowPlaying.title)+'</div><div class="a">'+esc(s.nowPlaying.artist||'')+'</div>'
      : '<div class="t">Nothing playing</div>';
    R('left').textContent=s.addsRemaining;
    R('queue').innerHTML=s.queue.map(e=>
      '<div class="row"><div class="meta"><div class="ti">'+esc(e.title)+'</div>'+
      '<div class="ar">'+esc(e.artist||'')+'</div></div>'+
      '<div class="votes">'+(e.votes>0?'&#128077; '+e.votes:'')+'</div>'+
      (s.voting?'<button data-vote="'+e.id+'" class="'+(e.voted?'voted':'ghost')+'">'+(e.voted?'&#10003;':'&#128077;')+'</button>':'')+
      '</div>').join('') || '<div class="hint">Queue is empty — add the first song!</div>';
    R('queue').querySelectorAll('[data-vote]').forEach(b=>{b.onclick=()=>vote(b.getAttribute('data-vote'));});
  }catch(e){}
}
async function vote(id){await api('/api/vote?id='+encodeURIComponent(id));refresh();}
let t=null;
R('q').addEventListener('input',e=>{
  clearTimeout(t);
  t=setTimeout(async()=>{
    const qv=e.target.value.trim();
    if(qv.length<2){R('results').innerHTML='';return;}
    const r=await api('/api/search?q='+encodeURIComponent(qv));
    R('results').innerHTML=r.results.map(s=>
      '<div class="row"><div class="meta"><div class="ti">'+esc(s.title)+'</div>'+
      '<div class="ar">'+esc(s.artist||'')+(s.album?' · '+esc(s.album):'')+'</div></div>'+
      '<button data-add="'+s.id+'">Add</button></div>').join('');
    R('results').querySelectorAll('[data-add]').forEach(b=>{b.onclick=()=>add(b.getAttribute('data-add'));});
  },350);
});
async function add(id){
  const r=await api('/api/add?id='+encodeURIComponent(id));
  if(!r.ok) alert(r.reason=='limit'?'You used all your adds!':('Could not add ('+r.reason+')'));
  R('q').value='';R('results').innerHTML='';refresh();
}
refresh();setInterval(refresh,3000);
</script></body></html>
"""
    }
}
