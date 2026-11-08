import java.util.*;

/**
 * Reproducible naive / KMP / Rabin-Karp comparison (Assignment 4, ANALYSIS.md).
 *
 * Build and run from the repository root:
 *   python tools/run.py --setup
 *   javac --release 17 -cp Java/build -d Java/build tests/student/SearchBench.java
 *   java -Xmx384m -cp Java/build SearchBench 42
 *
 * Timing scope: only the Algorithms.naive/kmp/rk call on an already-built input
 * map (no JSON parsing/printing, no JVM start-up, no compilation).
 * Warm-up: each method runs 20 times on the same input before measuring, so the
 * JIT has compiled the hot loops. Then 5 measured runs; we report all and the median.
 * Before timing, the three outputs are compared with each other.
 */
public final class SearchBench {
    static final int WARMUP = 20, RUNS = 5;

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int[][] sizes = {{750, 10}, {1500, 50}, {3000, 200}};
        System.out.println("java " + System.getProperty("java.version") + ", seed " + seed + ", times in microseconds");
        System.out.println("case|n|m|algorithm|runs|median|matches|outputs identical");
        for (String kind : new String[]{"random", "repetitive"}) {
            for (int[] nm : sizes) {
                Random rng = new Random(seed + nm[0] * 31L + nm[1] + kind.hashCode());
                String text, pattern;
                if (kind.equals("random")) {
                    // uniform over 26 lowercase letters; pattern copied from a random text position
                    text = randomString(rng, nm[0], "abcdefghijklmnopqrstuvwxyz");
                    int at = rng.nextInt(nm[0] - nm[1] + 1);
                    pattern = text.substring(at, at + nm[1]);
                } else {
                    // text = 'a'^n, pattern = 'a'^(m-1)'b' : many partial matches, worst case for naive
                    text = "a".repeat(nm[0]);
                    pattern = "a".repeat(nm[1] - 1) + "b";
                }
                Map<String, Object> in = new HashMap<>();
                in.put("text", text);
                in.put("pattern", pattern);
                Object a = Algorithms.naive(in).get("positions");
                Object b = Algorithms.kmp(in).get("positions");
                Object c = Algorithms.rk(in).get("positions");
                boolean same = a.equals(b) && b.equals(c);
                int z = ((List<?>) a).size();
                for (String alg : new String[]{"naive", "kmp", "rk"}) {
                    for (int i = 0; i < WARMUP; i++) call(alg, in);
                    long[] t = new long[RUNS];
                    for (int r = 0; r < RUNS; r++) {
                        long t0 = System.nanoTime();
                        call(alg, in);
                        t[r] = (System.nanoTime() - t0) / 1000;
                    }
                    long[] s = t.clone();
                    Arrays.sort(s);
                    System.out.println(kind + "|" + nm[0] + "|" + nm[1] + "|" + alg + "|" + Arrays.toString(t)
                            + "|" + s[RUNS / 2] + "|" + z + "|" + same);
                }
            }
        }
    }

    static Map<String, Object> call(String alg, Map<String, Object> in) {
        switch (alg) {
            case "naive": return Algorithms.naive(in);
            case "kmp": return Algorithms.kmp(in);
            default: return Algorithms.rk(in);
        }
    }

    static String randomString(Random rng, int n, String alpha) {
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) b.append(alpha.charAt(rng.nextInt(alpha.length())));
        return b.toString();
    }
}
