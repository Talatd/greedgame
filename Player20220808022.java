package players;

import game.*;
import java.util.*;

public class Player20220808022 extends Player {
    private static final long TIME_LIMIT = 900;
    private static final long TIME_LIMIT_MS = 997;
    private long startTime;
    private Random random = new Random();

    private Move firstMove;
    private boolean firstMoveUsed = false;

    public Player20220808022(Board board) {
        super(board);
        setInitialBestMove();
    }

    // İlk hamle için özel değerlendirme
    private void setInitialBestMove() {
        startTime = System.currentTimeMillis();
        List<Move> moves = board.getPossibleMoves();
        Move best = null;
        int maxScore = -1;

        for (Move move : moves) {
            if (timeLeft() <= 2) break;
            int score = evaluate(move);
            if (score > maxScore) {
                maxScore = score;
                best = move;
            }
        }

        this.firstMove = best != null ? best : moves.get(0);
    }

    @Override
    public Move nextMove() {
        if (board.getSize() == 25) {
            return monteCarlo25x25();
        } else {
            if (!firstMoveUsed) {
                firstMoveUsed = true;
                return firstMove;
            }
            return dfsStrategic();
        }
    }

    // 25x25 için round-robin Monte Carlo
    private Move monteCarlo25x25() {
        startTime = System.currentTimeMillis();
        List<Move> moves = board.getPossibleMoves();
        if (moves.isEmpty()) return null;

        int n = moves.size();
        int[] counts = new int[n];
        int[] totals = new int[n];

        while (!timeUp()) {
            for (int i = 0; i < n && !timeUp(); i++) {
                Board simBoard = new Board(board);
                if (!simBoard.applyMove(moves.get(i))) continue;
                int score = simulatePlayout(simBoard);
                counts[i]++;
                totals[i] += score;
            }
        }

        double bestAvg = -1;
        int bestIndex = 0;
        for (int i = 0; i < n; i++) {
            double avg = counts[i] > 0 ? (double) totals[i] / counts[i] : 0;
            if (avg > bestAvg) {
                bestAvg = avg;
                bestIndex = i;
            }
        }

        return moves.get(bestIndex);
    }

    // 10x10 & 50x50 için DFS ile skor hesaplaması
    private Move dfsStrategic() {
        startTime = System.currentTimeMillis();
        List<Move> moves = board.getPossibleMoves();
        if (moves.isEmpty()) return null;

        Move best = null;
        int maxScore = -1;

        for (Move move : moves) {
            if (timeLeft() <= 10) break;
            int score = evaluate(move);
            if (score > maxScore) {
                maxScore = score;
                best = move;
            }
        }

        return best != null ? best : moves.get(0);
    }

    // DFS-vari hamle simülasyonu
    private int evaluate(Move move) {
        try {
            return simulateWithDFS(board, move);
        } catch (Exception e) {
            return 0;
        }
    }

    private int simulateWithDFS(Board boardRef, Move move) throws TimeoutException {
        class SearchState {
            MyBoard board;
            int currentScore;
            int maxFuture;
            int index;
            List<Move> moves;

            SearchState(MyBoard b, int score, List<Move> m) {
                board = b;
                currentScore = score;
                maxFuture = 0;
                index = 0;
                moves = m;
            }
        }

        Stack<SearchState> stack = new Stack<>();
        MyBoard base = new MyBoard(boardRef);
        if (!base.applyMove(move)) return 0;

        int initialScore = base.getScore();
        List<Move> next = base.getPossibleMoves();
        if (next.isEmpty()) return initialScore;

        stack.push(new SearchState(base, initialScore, next));

        int maxTotal = initialScore;

        while (!stack.isEmpty()) {
            if (timeLeft() < 3) break;
            SearchState state = stack.peek();

            if (state.index < state.moves.size()) {
                Move nextMove = state.moves.get(state.index++);
                MyBoard nextBoard = new MyBoard(state.board);
                if (nextBoard.applyMove(nextMove)) {
                    int score = nextBoard.getScore();
                    List<Move> furtherMoves = nextBoard.getPossibleMoves();
                    if (furtherMoves.isEmpty()) {
                        state.maxFuture = Math.max(state.maxFuture, score);
                    } else {
                        stack.push(new SearchState(nextBoard, score, furtherMoves));
                    }
                }
            } else {
                int total = state.currentScore + state.maxFuture;
                maxTotal = Math.max(maxTotal, total);
                stack.pop();
                if (!stack.isEmpty()) {
                    stack.peek().maxFuture = Math.max(stack.peek().maxFuture, total);
                }
            }
        }

        return maxTotal;
    }

    private int simulatePlayout(Board sim) {
        while (!sim.isGameOver() && !timeUp()) {
            List<Move> moves = sim.getPossibleMoves();
            if (moves.isEmpty()) break;
            sim.applyMove(moves.get(random.nextInt(moves.size())));
        }
        return sim.getScore();
    }

    private boolean timeUp() {
        return System.currentTimeMillis() - startTime >= TIME_LIMIT;
    }

    private long timeLeft() {
        return TIME_LIMIT_MS - (System.currentTimeMillis() - startTime);
    }

    // ==================== MyBoard SINIFI ====================
    private class MyBoard {
        private final byte size;
        private final byte[][] grid;
        private final boolean[][] visited;
        private byte row, col;
        private int visitedCount = 0;

        public MyBoard(Board original) {
            this.size = (byte) original.getSize();
            this.grid = copyGrid(original.copyGrid());
            this.visited = copyVisited(original);
            this.row = (byte) original.getPlayerRow();
            this.col = (byte) original.getPlayerCol();
        }

        public MyBoard(MyBoard other) {
            this.size = other.size;
            this.grid = new byte[size][size];
            this.visited = new boolean[size][size];
            this.row = other.row;
            this.col = other.col;
            for (int i = 0; i < size; i++) {
                System.arraycopy(other.grid[i], 0, this.grid[i], 0, size);
                System.arraycopy(other.visited[i], 0, this.visited[i], 0, size);
            }
            this.visitedCount = other.visitedCount;
        }

        public int getScore() {
            return visitedCount;
        }

        public List<Move> getPossibleMoves() {
            List<Move> moves = new ArrayList<>();
            byte[][] dirs = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1},
                {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
            };

            for (byte[] dir : dirs) {
                byte dR = dir[0], dC = dir[1];
                if (canMove(dR, dC)) {
                    moves.add(new Move(dR, dC));
                }
            }
            return moves;
        }

        public boolean applyMove(Move move) {
            byte dR = (byte) move.getDRow();
            byte dC = (byte) move.getDCol();
            byte stepRow = (byte) (row + dR);
            byte stepCol = (byte) (col + dC);

            if (!inBounds(stepRow, stepCol) || visited[stepRow][stepCol]) return false;
            byte step = grid[stepRow][stepCol];
            byte destRow = (byte) (row + dR * step);
            byte destCol = (byte) (col + dC * step);

            if (!inBounds(destRow, destCol) || visited[destRow][destCol]) return false;

            row = destRow;
            col = destCol;
            visited[row][col] = true;
            grid[row][col] = 0;
            visitedCount++;
            return true;
        }

        private boolean canMove(byte dR, byte dC) {
            byte stepRow = (byte) (row + dR);
            byte stepCol = (byte) (col + dC);
            if (!inBounds(stepRow, stepCol) || visited[stepRow][stepCol]) return false;

            byte step = grid[stepRow][stepCol];
            byte destRow = (byte) (row + dR * step);
            byte destCol = (byte) (col + dC * step);

            return inBounds(destRow, destCol) && !visited[destRow][destCol];
        }

        private boolean inBounds(int r, int c) {
            return r >= 0 && r < size && c >= 0 && c < size;
        }

        private byte[][] copyGrid(int[][] src) {
            byte[][] copy = new byte[size][size];
            for (int i = 0; i < size; i++)
                for (int j = 0; j < size; j++)
                    copy[i][j] = (byte) src[i][j];
            return copy;
        }

        private boolean[][] copyVisited(Board original) {
            boolean[][] copy = new boolean[size][size];
            for (int i = 0; i < size; i++)
                for (int j = 0; j < size; j++)
                    copy[i][j] = original.isVisited(i, j);
            return copy;
        }
    }
}

class TimeoutException extends Exception {
    public TimeoutException(String message) {
        super(message);
    }
}
