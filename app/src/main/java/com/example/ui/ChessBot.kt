package com.example.ui

fun calculateBotMove(board: Array<Array<String>>, botColor: Char, difficulty: String): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
    val allMoves = mutableListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
    for (fy in 0..7) {
        for (fx in 0..7) {
            if (board[fy][fx].firstOrNull() == botColor) {
                for (ty in 0..7) {
                    for (tx in 0..7) {
                        if (isValidChessMove(board, fx, fy, tx, ty)) {
                            allMoves.add((fx to fy) to (tx to ty))
                        }
                    }
                }
            }
        }
    }
    if (allMoves.isEmpty()) return null

    val captures = allMoves.filter { board[it.second.second][it.second.first].isNotBlank() }
    
    val pVals = mapOf('P' to 1, 'N' to 3, 'B' to 3, 'R' to 5, 'Q' to 9, 'K' to 100)
    fun moveScore(m: Pair<Pair<Int, Int>, Pair<Int, Int>>): Int {
        val target = board[m.second.second][m.second.first]
        if (target.isBlank()) return 0
        return pVals[target[1]] ?: 0
    }

    return when (difficulty) {
        "hard" -> captures.maxByOrNull { moveScore(it) } ?: allMoves.random()
        "medium" -> if (captures.isNotEmpty() && Math.random() > 0.5) captures.random() else allMoves.random()
        else -> allMoves.random() // easy
    }
}
