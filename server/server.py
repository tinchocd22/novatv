#!/usr/bin/env python3
"""
NOVA TV - Servidor Flask para Android TV
Instalar: pip install flask requests
Correr:   python server.py
"""
from flask import Flask, jsonify, request, send_from_directory
import urllib.request, re, json, os, threading

app = Flask(__name__, static_folder='static', template_folder='static')

# ── Almacenamiento en memoria ──
state = {
    'items': [],
    'saved_url': '',
    'url_file': os.path.join(os.path.expanduser('~'), '.novatv_url')
}

# Cargar URL guardada al iniciar
try:
    if os.path.exists(state['url_file']):
        with open(state['url_file']) as f:
            state['saved_url'] = f.read().strip()
except: pass

VOD_EXT = re.compile(r'\.(mp4|mkv|avi|mov|wmv|flv|ts|m2ts|webm|mpg|mpeg)(\?.*)?$', re.I)

def is_vod(url):
    return bool(VOD_EXT.search(url))

def parse_m3u(text):
    items, cur = [], {}
    for line in text.splitlines():
        line = line.strip()
        if line.startswith('#EXTINF:'):
            cur = {'name': 'Item', 'group': 'Sin grupo', 'logo': '', 'url': '', 'vod': False}
            if ',' in line:
                cur['name'] = line.split(',', 1)[1].strip() or 'Item'
            for tag, key in [('tvg-name','name'), ('group-title','group'), ('tvg-logo','logo')]:
                m = re.search(rf'{tag}="([^"]*)"', line, re.I)
                if m and m.group(1):
                    cur[key] = m.group(1).strip()
        elif line and not line.startswith('#') and cur:
            cur['url'] = line
            cur['vod'] = is_vod(line)
            items.append(cur)
            cur = {}
    return items

def fetch_m3u(url):
    url = url.replace('github.com', 'raw.githubusercontent.com').replace('/blob/', '/')
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 VLC/3.0'})
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read().decode('utf-8', errors='replace')

# ── API endpoints ──

@app.route('/')
def index():
    return send_from_directory('static', 'index.html')

@app.route('/api/status')
def status():
    return jsonify({
        'items': len(state['items']),
        'saved_url': state['saved_url'],
        'live': sum(1 for c in state['items'] if not c['vod']),
        'vod': sum(1 for c in state['items'] if c['vod']),
    })

@app.route('/api/load', methods=['POST'])
def load_url():
    data = request.get_json()
    url = data.get('url', '').strip()
    if not url:
        return jsonify({'error': 'URL vacía'}), 400
    try:
        text = fetch_m3u(url)
        items = parse_m3u(text)
        if not items:
            return jsonify({'error': 'No se encontraron canales'}), 400
        state['items'] = items
        state['saved_url'] = url
        # Guardar URL
        try:
            with open(state['url_file'], 'w') as f: f.write(url)
        except: pass
        return jsonify({
            'ok': True,
            'total': len(items),
            'live': sum(1 for c in items if not c['vod']),
            'vod': sum(1 for c in items if c['vod']),
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/channels')
def channels():
    q = request.args.get('q', '').lower()
    items = [c for c in state['items'] if not c['vod']]
    if q:
        items = [c for c in items if q in c['name'].lower()]
    return jsonify(items)

@app.route('/api/movies')
def movies():
    q = request.args.get('q', '').lower()
    group = request.args.get('group', '')
    BAD = re.compile(r'^(sin grupo|)$', re.I)
    items = [c for c in state['items']
             if c['vod'] and not BAD.match(c['group'].strip())
             and not re.search(r'serie|season|temporada|episode|ep\b', c['group'], re.I)]
    if group:
        items = [c for c in items if c['group'] == group]
    if q:
        items = [c for c in items if q in c['name'].lower()]
    return jsonify(items)

@app.route('/api/series')
def series():
    q = request.args.get('q', '').lower()
    group = request.args.get('group', '')
    items = [c for c in state['items']
             if c['vod'] and re.search(r'serie|season|temporada|episode|ep\b', c['group'], re.I)]
    if group:
        items = [c for c in items if c['group'] == group]
    if q:
        items = [c for c in items if q in c['name'].lower()]
    return jsonify(items)

@app.route('/api/groups')
def groups():
    mode = request.args.get('mode', 'movies')
    BAD = re.compile(r'^(sin grupo|)$', re.I)
    if mode == 'series':
        items = [c for c in state['items']
                 if c['vod'] and re.search(r'serie|season|temporada|episode|ep\b', c['group'], re.I)]
    else:
        items = [c for c in state['items']
                 if c['vod'] and not BAD.match(c['group'].strip())
                 and not re.search(r'serie|season|temporada|episode|ep\b', c['group'], re.I)]
    from collections import Counter
    counts = Counter(c['group'] for c in items)
    groups = [{'name': k, 'count': v} for k, v in sorted(counts.items())]
    return jsonify(groups)

if __name__ == '__main__':
    print("NOVA TV Server arrancando en http://localhost:5000")
    print("Abrí esa URL en el navegador de tu Android TV")
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
