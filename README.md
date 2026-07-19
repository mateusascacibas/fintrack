*Built by Mateus Ascacibas — Java Backend Engineer*

## What I Learned Building This (Days 1–6)

### JVM Internals (Day 1–2)
- Stack frames are created per method call and destroyed on return.
  Setting `account = null` makes the object eligible for GC — the
  reference on the Stack is removed, but the object on the Heap
  waits for the collector.
- G1 GC divides the heap into equal-sized regions and collects the
  most garbage-dense regions first — hence "Garbage First".
- ZGC achieves sub-millisecond pauses via colored pointers and load
  barriers, allowing concurrent relocation without Stop-The-World.

### Reference Types (Day 3)
- WeakReference: GC collects on next cycle regardless of memory.
  SoftReference: GC collects only before OOM — ideal for caches.

### Concurrency (Day 4)
- `thread.run()` calls the method on the current thread — no new
  thread created. `thread.start()` is what creates a new OS thread.
- Virtual Threads (Java 21) suspend on I/O and unmount from the
  carrier thread, allowing high throughput without thread-per-request cost.

### Architecture Decision (Day 6)
- The domain layer has zero Spring/JPA imports. If I can test
  `account.debit(amount)` with pure JUnit — no Spring context needed —
  the architecture is correct.