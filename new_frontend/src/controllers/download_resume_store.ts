type DownloadResumeRecord = {
  key: string;
  virtualPath: string;
  fileName: string;
  etag: string;
  totalSize: number;
  chunkSize: number;
  loadedBytes: number;
  chunks: Array<Blob | null>;
  completedChunkIndexes: number[];
  fileHandle?: unknown;
  updatedAt: string;
};

const DB_NAME = "mpfm_download_resume";
const STORE_NAME = "records";
const DB_VERSION = 1;

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: "key" });
      }
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("open indexeddb failed"));
  });
  return dbPromise;
}

async function withStore<T>(mode: IDBTransactionMode, op: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await openDb();
  return new Promise<T>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, mode);
    const store = tx.objectStore(STORE_NAME);
    const request = op(store);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("indexeddb request failed"));
  });
}

/** 按 key 读取下载续传记录，不存在时返回 null。 */
export async function loadDownloadResumeRecord(key: string): Promise<DownloadResumeRecord | null> {
  const result = await withStore<DownloadResumeRecord | undefined>("readonly", (store) => store.get(key));
  return result ?? null;
}

/** 写入或覆盖下载续传记录，供暂停/续传恢复。 */
export async function saveDownloadResumeRecord(record: DownloadResumeRecord): Promise<void> {
  await withStore("readwrite", (store) => store.put(record));
}

/** 删除下载续传记录，通常在任务成功完成后调用。 */
export async function deleteDownloadResumeRecord(key: string): Promise<void> {
  await withStore("readwrite", (store) => store.delete(key));
}

export type { DownloadResumeRecord };
