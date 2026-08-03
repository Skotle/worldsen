package io.github.earthshape.diagnostics;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Writes an out-of-band thread dump when the logical server stops ticking. */
public final class ServerHangWatchdog {
   private static final long DUMP_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30L);
   private static final int MAX_DUMPS_PER_STALL = 3;
   private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
   private static final AtomicBoolean REGISTERED = new AtomicBoolean();
   private static volatile long lastTickNanos;
   private static volatile boolean running;
   private static volatile Thread watchdogThread;

   private ServerHangWatchdog() {
   }

   public static void register() {
      if (!REGISTERED.compareAndSet(false, true)) return;
      NeoForge.EVENT_BUS.addListener(ServerHangWatchdog::onServerStarted);
      NeoForge.EVENT_BUS.addListener(ServerHangWatchdog::onServerTick);
      NeoForge.EVENT_BUS.addListener(ServerHangWatchdog::onServerStopping);
   }

   private static void onServerStarted(ServerStartedEvent event) {
      lastTickNanos = System.nanoTime();
      running = true;
      Thread thread = new Thread(ServerHangWatchdog::watch, "EarthShape Hang Watchdog");
      thread.setDaemon(true);
      thread.setPriority(Thread.MAX_PRIORITY);
      watchdogThread = thread;
      thread.start();
   }

   private static void onServerTick(ServerTickEvent.Pre event) {
      lastTickNanos = System.nanoTime();
   }

   private static void onServerStopping(ServerStoppingEvent event) {
      running = false;
      Thread thread = watchdogThread;
      if (thread != null) thread.interrupt();
   }

   private static void watch() {
      long observedHeartbeat = lastTickNanos;
      int dumpCount = 0;
      while (running) {
         try {
            Thread.sleep(5000L);
         } catch (InterruptedException ignored) {
            if (!running) return;
         }

         long heartbeat = lastTickNanos;
         if (heartbeat != observedHeartbeat) {
            observedHeartbeat = heartbeat;
            dumpCount = 0;
         }

         long stalledNanos = System.nanoTime() - heartbeat;
         if (stalledNanos >= DUMP_INTERVAL_NANOS * (long)(dumpCount + 1) && dumpCount < MAX_DUMPS_PER_STALL) {
            dumpCount++;
            writeDump(stalledNanos, dumpCount);
         }
      }
   }

   private static void writeDump(long stalledNanos, int sequence) {
      try {
         Path directory = Path.of("logs");
         Files.createDirectories(directory);
         String timestamp = LocalDateTime.now().format(FILE_TIME);
         Path output = directory.resolve("earthshape-hang-" + timestamp + "-" + sequence + ".txt");
         try (FileOutputStream stream = new FileOutputStream(output.toFile());
              BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8))) {
            writeHeader(writer, stalledNanos, sequence);
            writeThreads(writer);
            writer.flush();
            stream.getFD().sync();
         }
      } catch (Throwable ignored) {
         // This path must never involve the normal logger or crash the watchdog.
      }
   }

   private static void writeHeader(BufferedWriter writer, long stalledNanos, int sequence) throws Exception {
      Runtime runtime = Runtime.getRuntime();
      long used = runtime.totalMemory() - runtime.freeMemory();
      writer.write("EarthShape out-of-band server hang dump\n");
      writer.write("time=" + LocalDateTime.now() + "\n");
      writer.write("sequence=" + sequence + "\n");
      writer.write("serverTickStalledMs=" + TimeUnit.NANOSECONDS.toMillis(stalledNanos) + "\n");
      writer.write("heapUsedBytes=" + used + "\n");
      writer.write("heapCommittedBytes=" + runtime.totalMemory() + "\n");
      writer.write("heapMaxBytes=" + runtime.maxMemory() + "\n");
      writer.write("availableProcessors=" + runtime.availableProcessors() + "\n");
      writer.write("systemLoadAverage=" + ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage() + "\n");
      for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
         writer.write("gc=" + collector.getName() + ", count=" + collector.getCollectionCount() + ", timeMs=" + collector.getCollectionTime() + "\n");
      }
      writer.write("\n");
   }

   private static void writeThreads(BufferedWriter writer) throws Exception {
      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      long[] deadlockedIds = threads.findDeadlockedThreads();
      Set<Long> deadlocked = new HashSet<>();
      if (deadlockedIds != null) Arrays.stream(deadlockedIds).forEach(deadlocked::add);
      writer.write("deadlockedThreadIds=" + deadlocked + "\n\n");

      for (ThreadInfo info : threads.dumpAllThreads(true, true)) {
         if (info == null) continue;
         writer.write('"' + info.getThreadName() + '"');
         writer.write(" id=" + info.getThreadId() + " state=" + info.getThreadState());
         if (deadlocked.contains(info.getThreadId())) writer.write(" DEADLOCKED");
         writer.write("\n");
         if (info.getLockName() != null) writer.write("  waitingOn=" + info.getLockName() + "\n");
         if (info.getLockOwnerName() != null) {
            writer.write("  lockOwner=\"" + info.getLockOwnerName() + "\" id=" + info.getLockOwnerId() + "\n");
         }
         for (StackTraceElement element : info.getStackTrace()) writer.write("    at " + element + "\n");
         for (MonitorInfo monitor : info.getLockedMonitors()) writer.write("    lockedMonitor " + monitor + "\n");
         for (LockInfo synchronizer : info.getLockedSynchronizers()) writer.write("    lockedSynchronizer " + synchronizer + "\n");
         writer.write("\n");
      }
   }
}
