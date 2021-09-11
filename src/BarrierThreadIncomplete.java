class BarrierThreadIncomplete implements Runnable {
    private int N = 0; // #threads
    private int X = 0; // iteration
    private int[] sync = null; // threads share it and synchronize on it.

    // constructor
    public BarrierThreadIncomplete(int[] sync, int nThreads, int iteration) {
        this.sync = sync;
        N = nThreads;
        X = iteration;
    }

    // the main body of this thread
    public void run() {
        for (int i = 0; i < X; i++) {
            barrier();
            System.out.println(i + " barriers completed by " +
                    Thread.currentThread());
        }
    }

    // this is what you implement
    private void barrier() {
        synchronized (sync) {
            // increment sync[0], because I reached the barrier
            sync[0]++;

            if (sync[0] != N) {
                try {
                    sync.wait();
                } catch (InterruptedException e) {
                }
            } else {
                sync.notifyAll();
                sync[0] = 0;
            }
        }
    }

    public static void main(String args[]) {
        // java BarrierThread #Threads iterations
        int[] sync = new int[1]; // used to count the number of threads that called barrier so far
        sync[0] = 0;
        int nThreads = Integer.parseInt(args[0]);
        int iteration = Integer.parseInt(args[1]);

        // spawn N - 1 child threads
        // this is what you implement
        for (int i = 0; i < nThreads - 1; i++) {
            new Thread(new BarrierThreadIncomplete(sync, nThreads, iteration)).start();
        }

        // the main calls run( ), too!
        (new BarrierThreadIncomplete(sync, nThreads, iteration)).run();
    }
}
