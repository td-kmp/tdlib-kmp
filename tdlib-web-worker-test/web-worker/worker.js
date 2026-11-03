// TDLib's Web Worker: copy it into your application. It maps WorkerTdLib's commands (see its KDoc) to the npm package
// and owns storage: /tdlib is kept in IndexedDB, so database_directory goes under it. Everything else is in memory.
import initTdlib, * as td from '@td-kmp/tdlib-wasm';

const ROOT = `/${self.name || 'tdlib'}`;

const early = [];
let handle = (message) => early.push(message);
onmessage = ({ data }) => handle(data);

const fail = (what) => postMessage({ error: String(what?.message ?? what) });

const syncfs = (populate) =>
  new Promise((resolve, reject) => td.FS.syncfs(populate, (e) => (e ? reject(e) : resolve())));

// One at a time: a write that is already running may not include what has changed since it started.
let writes = Promise.resolve();
const write = () => (writes = writes.then(() => syncfs(false)).catch(fail));

const commands = {
  create: () => { td.createClientId(); },
  send: ({ clientId, request }) => { td.send(clientId, request); },
  receive: async () => {
    const events = [];
    for (let event; (event = td.receive()) !== null;) events.push(event);
    // A client is reported closed only once its database is durable.
    if (events.some((event) => event.includes('"authorizationStateClosed"'))) await write();
    return { events, timeout: td.getTimeout() };
  },
  execute: ({ request }) => ({ result: td.execute(request) }),
  setLogMessageHandler: ({ maxVerbosityLevel, enabled }) => {
    td.setLogMessageHandler(maxVerbosityLevel, enabled ? (level, message) => postMessage({ level, message }) : null);
  },
};

async function start() {
  await initTdlib({ onAbort: (what) => fail(`TDLib aborted: ${what}`) });
  td.FS.mkdir(ROOT);
  td.FS.mount(td.IDBFS, {}, ROOT);
  await syncfs(true);
  setInterval(write, 5000);
  for (const event of ['open', 'message', 'close', 'error']) td.onSocketEvent(event, () => postMessage({ wake: true }));
  handle = async ({ id, data }) => {
    try {
      const answer = (await commands[data.cmd](data)) ?? {};
      if (id !== undefined) postMessage({ id, data: answer });
    } catch (e) {
      if (id !== undefined) postMessage({ id, error: String(e?.message ?? e) });
      else fail(e);
    }
  };
  early.splice(0).forEach(handle);
}

start().catch(fail);
