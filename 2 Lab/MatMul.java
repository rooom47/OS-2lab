import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class MatMul {

    // геннерим
    static long[][] randomMatrix(int rows, int cols, Random rnd) {
        long[][] a = new long[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                a[i][j] = rnd.nextInt(10); // 0..9
        return a;
    }

    // вычисление блока строк [from, to), порядок i -> t -> j, внутренний цикл идёт подряд по строкам B и C 
    static void multiplyRowsIKJ(long[][] A, long[][] B, long[][] C, int from, int to,
                                AtomicInteger done) {
        int m = B.length, k = B[0].length;
        for (int i = from; i < to; i++) {
            long[] Ci = C[i];
            for (int t = 0; t < m; t++) {
                long a = A[i][t];
                long[] Bt = B[t];
                for (int j = 0; j < k; j++)
                    Ci[j] += a * Bt[j];
            }
            if (done != null) done.incrementAndGet(); //общий счётчик прогресса
        }
    }

    //"школьный" порядок i -> j -> t, B читается по столбцу 
    static void multiplyRowsIJT(long[][] A, long[][] B, long[][] C, int from, int to,
                                AtomicInteger done) {
        int m = B.length, k = B[0].length;
        for (int i = from; i < to; i++) {
            for (int j = 0; j < k; j++) {
                long s = 0;
                for (int t = 0; t < m; t++)
                    s += A[i][t] * B[t][j];
                C[i][j] = s;
            }
            if (done != null) done.incrementAndGet();
        }
    }

    //многопоточное умножение. возвращаем матрицу C. строчки делятся на T непрерывных блоков, остаток раздаётся по одной строке первым потокам. printProgress=false отключает вывод(нужно для замеров).
    static long[][] multiply(long[][] A, long[][] B, int T, boolean ijt, boolean printProgress)
            throws InterruptedException {
        int n = A.length, k = B[0].length;
        long[][] C = new long[n][k];
        AtomicInteger done = new AtomicInteger(0);
        Thread[] threads = new Thread[T];

        int base = n / T, rem = n % T, start = 0;
        for (int id = 0; id < T; id++) {
            int cnt = base + (id < rem ? 1 : 0); // при T > n часть потоков получит 0 строк
            final int from = start, to = start + cnt;
            start = to;
            threads[id] = new Thread(() -> {
                if (ijt) multiplyRowsIJT(A, B, C, from, to, done);
                else multiplyRowsIKJ(A, B, C, from, to, done);
            });
            threads[id].start();
        }

        //главный поток печатает прогресс, пока рабочие считают
        if (printProgress) {
            int last = -1;
            while (done.get() < n) {
                int pct = (int) (100L * done.get() / n);
                if (pct != last) {
                    System.out.print("\rПрогресс: " + pct + "%");
                    last = pct;
                }
                Thread.sleep(200);
            }
        }
        for (Thread th : threads) th.join();
        if (printProgress) System.out.println("\rПрогресс: 100%");
        return C;
    }

    static long checksum(long[][] C) {
        long s = 0;
        for (long[] row : C) for (long v : row) s += v;
        return s;
    }

    //серия замеров 
    static double timeOnce(long[][] A, long[][] B, int T) throws InterruptedException {
        long t0 = System.nanoTime();
        long[][] C = multiply(A, B, T, false, false);
        double sec = (System.nanoTime() - t0) / 1e9;
        if (checksum(C) == Long.MIN_VALUE) System.out.println(); //чтобы JIT не выбросил расчёт
        return sec;
    }

    static void bench(int[] sizes, int runs) throws Exception {
        int logical = Runtime.getRuntime().availableProcessors();
        //logical с шагом 1, затем 4x и 8x
        int[] ts = new int[2 * logical + 2];
        for (int i = 0; i < 2 * logical; i++) ts[i] = i + 1;
        ts[2 * logical] = 4 * logical;
        ts[2 * logical + 1] = 8 * logical;
        System.out.println("Логических процессоров: " + logical);

        StringBuilder csv = new StringBuilder("size,threads,avg_sec\n");
        for (int n : sizes) {
            Random rnd = new Random(42);
            long[][] A = randomMatrix(n, n, rnd), B = randomMatrix(n, n, rnd);
            timeOnce(A, B, logical); //прогрев JIT, не учитывается
            long ref = -1;
            for (int T : ts) {
                double[] r = new double[runs];
                for (int i = 0; i < runs; i++) r[i] = timeOnce(A, B, T);
                Arrays.sort(r);
                double sum = 0; //отбрасываю min и max
                for (int i = 1; i < runs - 1; i++) sum += r[i];
                double avg = sum / (runs - 2);
                csv.append(n).append(',').append(T).append(',')
                   .append(String.format(java.util.Locale.US, "%.4f", avg)).append('\n');
                System.out.printf(java.util.Locale.US, "n=%d T=%d avg=%.3f s%n", n, T, avg);
            }
            //проверочка, контрольная сумма не зависит от числа потоков
            long s1 = checksum(multiply(A, B, 1, false, false));
            long s2 = checksum(multiply(A, B, logical, false, false));
            System.out.println("Проверка n=" + n + ": " + (s1 == s2 ? "OK" : "ОШИБКА!!"));
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("results.csv"), csv.toString());
        System.out.println("Результаты записаны в results.csv");
    }

    // сравнение порядков циклов (1 поток)
    // Прогреваем JIT, многократно вызываем оба метода на маленькой матрице, чтобы они были полностью оптимизированы и сравнение было честным
    static void warmUpJit() {
        Random rnd = new Random(1);
        long[][] a = randomMatrix(100, 100, rnd), b = randomMatrix(100, 100, rnd);
        long[][] c = new long[100][100];
        AtomicInteger dummy = new AtomicInteger(); // не null, как в реальных вызовах
        for (int it = 0; it < 3000; it++)
            for (int r = 0; r < 100; r += 20) {
                multiplyRowsIKJ(a, b, c, r, r + 20, dummy);
                multiplyRowsIJT(a, b, c, r, r + 20, dummy);
            }
    }

    static void loopOrder(int n) throws Exception {
        warmUpJit();
        Random rnd = new Random(42);
        long[][] A = randomMatrix(n, n, rnd), B = randomMatrix(n, n, rnd);
        long t0 = System.nanoTime();
        long s1 = checksum(multiply(A, B, 1, false, false));
        double ikj = (System.nanoTime() - t0) / 1e9;
        t0 = System.nanoTime();
        long s2 = checksum(multiply(A, B, 1, true, false));
        double ijt = (System.nanoTime() - t0) / 1e9;
        System.out.printf(java.util.Locale.US, "n=%d: i-t-j = %.3f s, i-j-t = %.3f s, суммы %s%n",
                n, ikj, ijt, s1 == s2 ? "совпали" : "НЕ СОВПАЛИ!!");
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equals("bench")) {
            String[] parts = args[1].split(",");
            int[] sizes = new int[parts.length];
            for (int i = 0; i < parts.length; i++) sizes[i] = Integer.parseInt(parts[i]);
            bench(sizes, args.length > 2 ? Integer.parseInt(args[2]) : 5);
            return;
        }
        if (args.length == 2 && args[0].equals("loops")) {
            loopOrder(Integer.parseInt(args[1]));
            return;
        }
        if (args.length < 4) {
            System.out.println("Использование: java MatMul n m k T [ikj|ijt]\n"
                    + "              java MatMul bench 500,1000,1500 [runs]\n"
                    + "              java MatMul loops N");
            return;
        }
        int n = Integer.parseInt(args[0]), m = Integer.parseInt(args[1]);
        int k = Integer.parseInt(args[2]), T = Integer.parseInt(args[3]);
        boolean ijt = args.length > 4 && args[4].equals("ijt");
        if (n < 1 || m < 1 || k < 1 || T < 1) {
            System.out.println("Все параметры должны быть >= 1");
            return;
        }
        Random rnd = new Random();
        long[][] A = randomMatrix(n, m, rnd), B = randomMatrix(m, k, rnd);

        long t0 = System.nanoTime();
        long[][] C = multiply(A, B, T, ijt, true);
        double sec = (System.nanoTime() - t0) / 1e9;

        if (n <= 10 && k <= 10) {
            for (long[] row : C) System.out.println(Arrays.toString(row));
        }
        System.out.printf(java.util.Locale.US, "Время умножения: %.3f с%n", sec);
        System.out.println("Контрольная сумма: " + checksum(C));
    }
}
