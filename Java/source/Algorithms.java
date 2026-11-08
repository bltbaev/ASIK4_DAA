import java.util.*;

/** Assignment 4 algorithms. docs/guide/CONTRACTS.md defines each field and limit. */
public final class Algorithms {

    // ------------------------------------------------------------------ helpers
    private static String str(Map<String, Object> in, String key) {
        Object v = in.get(key);
        return v == null ? "" : (String) v;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> in, String key) {
        Object v = in.get(key);
        return v == null ? new ArrayList<>() : (List<Object>) v;
    }

    private static Map<String, Object> positions(List<Long> pos) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("positions", pos);
        return out;
    }

    /** Empty pattern matches every boundary 0..n (shared by naive, kmp and rk). */
    private static List<Long> allBoundaries(int n) {
        List<Long> pos = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) pos.add((long) i);
        return pos;
    }

    // ------------------------------------------------------------------ Part 1: LCS
    /**
     * dp[i][j] = LCS length of suffixes a[i..] and b[j..] (suffix form lets us
     * reconstruct forwards). Stored in one flat int array of (|a|+1)(|b|+1).
     */
    static Map<String, Object> lcs(Map<String, Object> input) {
        String a = str(input, "a"), b = str(input, "b");
        int n = a.length(), m = b.length();
        Map<String, Object> out = new LinkedHashMap<>();
        if (n == 0 || m == 0) {
            out.put("length", 0L);
            out.put("sequence", "");
            return out;
        }
        int w = m + 1;
        int[] dp = new int[(n + 1) * w]; // row n and column m are 0 (base cases)
        for (int i = n - 1; i >= 0; i--) {
            char ca = a.charAt(i);
            int row = i * w, next = (i + 1) * w;
            for (int j = m - 1; j >= 0; j--) {
                if (ca == b.charAt(j)) dp[row + j] = dp[next + j + 1] + 1;
                else dp[row + j] = Math.max(dp[next + j], dp[row + j + 1]);
            }
        }
        StringBuilder seq = new StringBuilder(dp[0]);
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.charAt(i) == b.charAt(j)) { seq.append(a.charAt(i)); i++; j++; }
            else if (dp[(i + 1) * w + j] >= dp[i * w + j + 1]) i++;
            else j++;
        }
        out.put("length", (long) dp[0]);
        out.put("sequence", seq.toString());
        return out;
    }

    // ------------------------------------------------------------------ Part 2: interval merge
    /**
     * cost[l][r] = minimum cost to merge blocks l..r into one block.
     * cost[l][r] = min_{l<=k<r} cost[l][k] + cost[k+1][r] + sum(l..r); cost[i][i]=0.
     */
    static Map<String, Object> merge(Map<String, Object> input) {
        List<Object> sz = list(input, "sizes");
        int n = sz.size();
        Map<String, Object> out = new LinkedHashMap<>();
        List<List<Long>> plan = new ArrayList<>();
        if (n <= 1) {
            out.put("cost", 0L);
            out.put("plan", plan);
            return out;
        }
        long[] pre = new long[n + 1];
        for (int i = 0; i < n; i++) pre[i + 1] = pre[i] + ((Number) sz.get(i)).longValue();
        long[][] cost = new long[n][n];
        int[][] split = new int[n][n];
        for (int len = 2; len <= n; len++) {
            for (int l = 0; l + len - 1 < n; l++) {
                int r = l + len - 1;
                long best = Long.MAX_VALUE;
                int bestK = l;
                for (int k = l; k < r; k++) {
                    long c = cost[l][k] + cost[k + 1][r];
                    if (c < best) { best = c; bestK = k; }
                }
                cost[l][r] = best + (pre[r + 1] - pre[l]);
                split[l][r] = bestK;
            }
        }
        // Post-order (children before parent) without recursion.
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1, 0});
        while (!stack.isEmpty()) {
            int[] f = stack.peek();
            int l = f[0], r = f[1];
            if (l == r) { stack.pop(); continue; }
            int k = split[l][r];
            if (f[2] == 0) {
                f[2] = 1;
                stack.push(new int[]{k + 1, r, 0}); // processed second
                stack.push(new int[]{l, k, 0});     // processed first
            } else {
                stack.pop();
                plan.add(List.of((long) l, (long) k, (long) r));
            }
        }
        out.put("cost", cost[0][n - 1]);
        out.put("plan", plan);
        return out;
    }

    // ------------------------------------------------------------------ Part 3: tree MWIS
    /**
     * take[v] = w[v] + sum skip[c];  skip[v] = sum max(take[c], skip[c]).
     * Iterative BFS from root 0, then reverse BFS order acts as post-order.
     */
    static Map<String, Object> tree(Map<String, Object> input) {
        int n = ((Number) input.get("n")).intValue();
        List<Object> wl = list(input, "weights");
        List<Object> el = list(input, "edges");
        Map<String, Object> out = new LinkedHashMap<>();
        List<Long> selected = new ArrayList<>();
        if (n == 0) {
            out.put("weight", 0L);
            out.put("selected", selected);
            return out;
        }
        long[] w = new long[n];
        for (int i = 0; i < n; i++) w[i] = ((Number) wl.get(i)).longValue();
        // Adjacency in CSR form.
        int[] deg = new int[n + 1];
        int[] eu = new int[el.size()], ev = new int[el.size()];
        for (int e = 0; e < el.size(); e++) {
            List<?> pair = (List<?>) el.get(e);
            eu[e] = ((Number) pair.get(0)).intValue();
            ev[e] = ((Number) pair.get(1)).intValue();
            deg[eu[e]]++; deg[ev[e]]++;
        }
        int[] start = new int[n + 1];
        for (int i = 0; i < n; i++) start[i + 1] = start[i] + deg[i];
        int[] fill = Arrays.copyOf(start, n);
        int[] adj = new int[2 * el.size()];
        for (int e = 0; e < el.size(); e++) {
            adj[fill[eu[e]]++] = ev[e];
            adj[fill[ev[e]]++] = eu[e];
        }
        int[] order = new int[n], parent = new int[n];
        Arrays.fill(parent, -1);
        boolean[] seen = new boolean[n];
        int head = 0, tail = 0;
        order[tail++] = 0; seen[0] = true;
        while (head < tail) {
            int v = order[head++];
            for (int p = start[v]; p < start[v + 1]; p++) {
                int u = adj[p];
                if (!seen[u]) { seen[u] = true; parent[u] = v; order[tail++] = u; }
            }
        }
        long[] take = new long[n], skip = new long[n];
        for (int idx = n - 1; idx >= 0; idx--) {
            int v = order[idx];
            take[v] += w[v];
            int p = parent[v];
            if (p >= 0) {
                take[p] += skip[v];
                skip[p] += Math.max(take[v], skip[v]);
            }
        }
        // Top-down reconstruction: a child of a taken vertex is skipped.
        boolean[] chosen = new boolean[n];
        for (int idx = 0; idx < n; idx++) {
            int v = order[idx];
            int p = parent[v];
            if (p >= 0 && chosen[p]) continue;
            if (take[v] > skip[v]) { chosen[v] = true; selected.add((long) v); }
        }
        int root = order[0];
        out.put("weight", Math.max(take[root], skip[root]));
        out.put("selected", selected);
        return out;
    }

    // ------------------------------------------------------------------ Part 4: naive
    static Map<String, Object> naive(Map<String, Object> input) {
        String t = str(input, "text"), p = str(input, "pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return positions(allBoundaries(n));
        List<Long> pos = new ArrayList<>();
        for (int s = 0; s + m <= n; s++) {
            int j = 0;
            while (j < m && t.charAt(s + j) == p.charAt(j)) j++;
            if (j == m) pos.add((long) s);
        }
        return positions(pos);
    }

    // ------------------------------------------------------------------ Part 4: KMP
    /** pi[i] = length of the longest proper border of p[0..i]. */
    static int[] prefixFunction(String p) {
        int m = p.length();
        int[] pi = new int[m];
        for (int i = 1; i < m; i++) {
            int k = pi[i - 1];
            while (k > 0 && p.charAt(i) != p.charAt(k)) k = pi[k - 1];
            if (p.charAt(i) == p.charAt(k)) k++;
            pi[i] = k;
        }
        return pi;
    }

    static Map<String, Object> kmp(Map<String, Object> input) {
        String t = str(input, "text"), p = str(input, "pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return positions(allBoundaries(n));
        List<Long> pos = new ArrayList<>();
        if (m > n) return positions(pos);
        int[] pi = prefixFunction(p);
        int k = 0; // number of pattern characters currently matched
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            while (k > 0 && c != p.charAt(k)) k = pi[k - 1];
            if (c == p.charAt(k)) k++;
            if (k == m) {
                pos.add((long) (i - m + 1));
                k = pi[k - 1]; // fallback keeps overlapping matches
            }
        }
        return positions(pos);
    }

    // ------------------------------------------------------------------ Part 4: Rabin-Karp
    static final long RK_BASE = 31, RK_MOD = 101;

    static Map<String, Object> rk(Map<String, Object> input) {
        String t = str(input, "text"), p = str(input, "pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return positions(allBoundaries(n));
        List<Long> pos = new ArrayList<>();
        if (m > n) return positions(pos);
        long high = 1; // BASE^(m-1) mod MOD: weight of the leftmost character
        for (int i = 1; i < m; i++) high = high * RK_BASE % RK_MOD;
        long hp = 0, hw = 0;
        for (int i = 0; i < m; i++) {
            hp = (hp * RK_BASE + p.charAt(i)) % RK_MOD;
            hw = (hw * RK_BASE + t.charAt(i)) % RK_MOD;
        }
        for (int s = 0; ; s++) {
            if (hp == hw) { // candidate only: verify characters
                int j = 0;
                while (j < m && t.charAt(s + j) == p.charAt(j)) j++;
                if (j == m) pos.add((long) s);
            }
            if (s + m >= n) break;
            hw = (hw - t.charAt(s) * high) % RK_MOD;
            if (hw < 0) hw += RK_MOD; // normalize negative remainder
            hw = (hw * RK_BASE + t.charAt(s + m)) % RK_MOD;
        }
        return positions(pos);
    }

    // ------------------------------------------------------------------ Part 5: suffix array + Kasai
    /**
     * Prefix doubling: suffixes are sorted by (rank[i], rank[i+k]) with -1 for
     * a missing second half, using two stable counting-sort passes per round
     * (O(n log n)). No sentinel, no materialised suffix strings.
     */
    static int[] suffixArray(String s) {
        int n = s.length();
        int[] sa = new int[n], rank = new int[n], tmp = new int[n], cnt;
        int[] sa2 = new int[n];
        // Round 0: sort by first character (ASCII 0..127).
        cnt = new int[Math.max(128, n) + 1];
        for (int i = 0; i < n; i++) cnt[s.charAt(i)]++;
        for (int c = 1; c < 128; c++) cnt[c] += cnt[c - 1];
        for (int i = n - 1; i >= 0; i--) sa[--cnt[s.charAt(i)]] = i;
        rank[sa[0]] = 0;
        int classes = 1;
        for (int i = 1; i < n; i++) {
            if (s.charAt(sa[i]) != s.charAt(sa[i - 1])) classes++;
            rank[sa[i]] = classes - 1;
        }
        for (int k = 1; k < n && classes < n; k <<= 1) {
            // Key2 = rank[i+k] + 1 (0 means "missing"). Counting sort by key2.
            Arrays.fill(cnt, 0, classes + 2, 0);
            for (int i = 0; i < n; i++) cnt[i + k < n ? rank[i + k] + 1 : 0]++;
            for (int c = 1; c <= classes; c++) cnt[c] += cnt[c - 1];
            for (int i = n - 1; i >= 0; i--) sa2[--cnt[i + k < n ? rank[i + k] + 1 : 0]] = i;
            // Stable counting sort by key1 = rank[i].
            Arrays.fill(cnt, 0, classes + 1, 0);
            for (int i = 0; i < n; i++) cnt[rank[i]]++;
            for (int c = 1; c < classes; c++) cnt[c] += cnt[c - 1];
            for (int idx = n - 1; idx >= 0; idx--) { int i = sa2[idx]; sa[--cnt[rank[i]]] = i; }
            // Re-rank by equal pairs.
            tmp[sa[0]] = 0;
            classes = 1;
            for (int idx = 1; idx < n; idx++) {
                int x = sa[idx], y = sa[idx - 1];
                int x2 = x + k < n ? rank[x + k] : -1, y2 = y + k < n ? rank[y + k] : -1;
                if (rank[x] != rank[y] || x2 != y2) classes++;
                tmp[x] = classes - 1;
            }
            int[] sw = rank; rank = tmp; tmp = sw;
        }
        return sa;
    }

    /** Kasai: lcp[i] compares sa[i-1] and sa[i]; h drops by at most one per step. */
    static int[] kasai(String s, int[] sa) {
        int n = s.length();
        int[] rank = new int[n], lcp = new int[n];
        for (int i = 0; i < n; i++) rank[sa[i]] = i;
        int h = 0;
        for (int i = 0; i < n; i++) {
            if (rank[i] == 0) { h = 0; continue; }
            int j = sa[rank[i] - 1];
            while (i + h < n && j + h < n && s.charAt(i + h) == s.charAt(j + h)) h++;
            lcp[rank[i]] = h;
            if (h > 0) h--;
        }
        return lcp;
    }

    static Map<String, Object> suffix(Map<String, Object> input) {
        String t = str(input, "text");
        int n = t.length();
        Map<String, Object> out = new LinkedHashMap<>();
        List<Long> saOut = new ArrayList<>(n), lcpOut = new ArrayList<>(n);
        String repeated = "";
        if (n > 0) {
            int[] sa = suffixArray(t);
            int[] lcp = kasai(t, sa);
            int best = 0, at = -1;
            for (int i = 0; i < n; i++) {
                saOut.add((long) sa[i]);
                lcpOut.add((long) lcp[i]);
                if (lcp[i] > best) { best = lcp[i]; at = sa[i]; }
            }
            if (best > 0) repeated = t.substring(at, at + best);
        }
        out.put("sa", saOut);
        out.put("lcp", lcpOut);
        out.put("repeated", repeated);
        return out;
    }

    // ------------------------------------------------------------------ Part 6: Aho-Corasick
    static final int ALPHA = 95, FIRST = 32; // printable ASCII 32..126

    /**
     * Trie in a flat goto table (states x 95). Failure links by BFS turn it into
     * a full automaton. For each text, count visits per state, then push the
     * visit counts from deeper states to their failure targets in reverse BFS
     * order; a state's final count is the number of text positions whose
     * current longest match has this state's string as a suffix.
     */
    static Map<String, Object> aho(Map<String, Object> input) {
        List<Object> pats = list(input, "patterns");
        List<Object> texts = list(input, "texts");
        int p = pats.size();
        Map<String, Object> out = new LinkedHashMap<>();
        List<List<Long>> counts = new ArrayList<>(texts.size());
        int total = 0;
        for (Object o : pats) total += ((String) o).length();
        int cap = total + 1;
        int[] go = new int[cap * ALPHA];
        Arrays.fill(go, -1);
        int states = 1;
        int[] term = new int[p]; // terminal state for each dictionary position
        for (int i = 0; i < p; i++) {
            String s = (String) pats.get(i);
            int v = 0;
            for (int j = 0; j < s.length(); j++) {
                int c = s.charAt(j) - FIRST;
                if (go[v * ALPHA + c] < 0) go[v * ALPHA + c] = states++;
                v = go[v * ALPHA + c];
            }
            term[i] = v; // duplicates share the same terminal state
        }
        int[] fail = new int[states], bfs = new int[states];
        int head = 0, tail = 0;
        for (int c = 0; c < ALPHA; c++) {
            int u = go[c];
            if (u < 0) go[c] = 0;
            else { fail[u] = 0; bfs[tail++] = u; }
        }
        while (head < tail) {
            int v = bfs[head++];
            for (int c = 0; c < ALPHA; c++) {
                int u = go[v * ALPHA + c];
                if (u < 0) go[v * ALPHA + c] = go[fail[v] * ALPHA + c];
                else { fail[u] = go[fail[v] * ALPHA + c]; bfs[tail++] = u; }
            }
        }
        long[] visits = new long[states];
        for (Object to : texts) {
            String t = (String) to;
            Arrays.fill(visits, 0L); // independent text: fresh counts
            int v = 0;
            for (int i = 0; i < t.length(); i++) {
                v = go[v * ALPHA + (t.charAt(i) - FIRST)];
                visits[v]++;
            }
            for (int idx = tail - 1; idx >= 0; idx--) { // deeper states first
                int u = bfs[idx];
                visits[fail[u]] += visits[u];
            }
            List<Long> row = new ArrayList<>(p);
            for (int i = 0; i < p; i++) row.add(visits[term[i]]);
            counts.add(row);
        }
        out.put("counts", counts);
        return out;
    }
}
