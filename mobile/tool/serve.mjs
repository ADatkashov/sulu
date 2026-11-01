// Tiny static server for build/web with /api and /media proxied to the backend (same-origin, no CORS). node tool/serve.mjs [port]
import http from 'node:http'
import { readFile } from 'node:fs/promises'
import { extname, join, normalize } from 'node:path'
const port = Number(process.argv[2] ?? 5174), api = new URL(process.env.SULU_API_URL ?? 'http://localhost:8080'), root = new URL('../build/web', import.meta.url).pathname
const types = { '.html': 'text/html', '.js': 'text/javascript', '.json': 'application/json', '.wasm': 'application/wasm', '.png': 'image/png', '.svg': 'image/svg+xml', '.otf': 'font/otf', '.ttf': 'font/ttf', '.ico': 'image/x-icon' }
http.createServer(async (req, res) => {
  if (req.url.startsWith('/api') || req.url.startsWith('/media')) {
    const p = http.request({ host: api.hostname, port: api.port, path: req.url, method: req.method, headers: (({ origin, ...h }) => ({ ...h, host: api.host }))(req.headers) }, (r) => { res.writeHead(r.statusCode, r.headers); r.pipe(res) })
    p.on('error', () => { res.writeHead(502); res.end() }); req.pipe(p); return
  }
  let f = normalize(join(root, req.url.split('?')[0])); if (!f.startsWith(root)) { res.writeHead(403); return res.end() }
  if (f.endsWith('/')) f = join(f, 'index.html')
  try { const b = await readFile(f); res.writeHead(200, { 'content-type': types[extname(f)] ?? 'application/octet-stream' }); res.end(b) }
  catch { try { const b = await readFile(join(root, 'index.html')); res.writeHead(200, { 'content-type': 'text/html' }); res.end(b) } catch { res.writeHead(404); res.end() } }
}).listen(port, () => console.log('serving', root, 'on', port))
