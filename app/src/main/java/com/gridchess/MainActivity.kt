package com.gridchess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.bhlangonijr.chesslib.Side
import com.gridchess.game.GameViewModel
import com.gridchess.ui.screens.GameScreen
import com.gridchess.ui.screens.HomeScreen
import com.gridchess.ui.theme.GridChessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GridChessTheme {
                val vm: GameViewModel = viewModel()
                var inGame by remember { mutableStateOf(false) }

                if (inGame) {
                    GameScreen(
                        viewModel = vm,
                        onExit = { inGame = false },
                    )
                } else {
                    HomeScreen(
                        onStart = { difficulty, side: Side ->
                            vm.newGame(difficulty, side)
                            inGame = true
                        },
                    )
                }
            }
        }
    }
}
