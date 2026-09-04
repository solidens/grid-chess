#!/usr/bin/env python3
"""
Cross-check LeelaEncoder.kt against lc0's own input encoding.

A bug in the 112-plane encoding would not crash anything -- the network would
still return a legal move, just a worse one -- so it needs a reference to be
checked against rather than eyeballing. This mirrors the Kotlin encoder line
for line in Python, then diffs it against the tensor lczerolens gets straight
out of lc0 for the same positions.

Run it after tools/export_maia.py:

    scratch/.venv/bin/python tools/verify_encoder.py

Requires: lczerolens==0.3.3, lczero-bindings, onnxruntime.
"""
from __future__ import annotations

import sys
from pathlib import Path

import chess
import numpy as np

ROOT = Path(__file__).resolve().parent.parent
MODELS = ROOT / "app" / "src" / "main" / "assets" / "models"
POLICY_INDEX = ROOT / "app" / "src" / "main" / "assets" / "policy_index.txt"

PIECE_ORDER = [chess.PAWN, chess.KNIGHT, chess.BISHOP, chess.ROOK, chess.QUEEN, chess.KING]
HISTORY = 8
PLANES_PER_BOARD = 13
AUX = PLANES_PER_BOARD * HISTORY


def encode(board: chess.Board) -> np.ndarray:
    """The Kotlin LeelaEncoder, transliterated. Keep the two in step."""
    out = np.zeros((112, 64), dtype=np.float32)

    us = board.turn
    them = not us
    mirror = us == chess.BLACK

    # Walk the move stack backwards to recover the position history.
    positions: list[chess.Board] = []
    probe = board.copy()
    for _ in range(HISTORY):
        positions.append(probe.copy())
        if not probe.move_stack:
            break
        probe.pop()

    for i, pos in enumerate(positions):
        base = i * PLANES_PER_BOARD
        for p, piece in enumerate(PIECE_ORDER):
            out[base + p] = bits(pos.pieces_mask(piece, us), mirror)
            out[base + 6 + p] = bits(pos.pieces_mask(piece, them), mirror)
        if pos.is_repetition(2):
            out[base + 12] = 1.0

    if board.has_queenside_castling_rights(us):
        out[AUX + 0] = 1.0
    if board.has_kingside_castling_rights(us):
        out[AUX + 1] = 1.0
    if board.has_queenside_castling_rights(them):
        out[AUX + 2] = 1.0
    if board.has_kingside_castling_rights(them):
        out[AUX + 3] = 1.0
    if mirror:
        out[AUX + 4] = 1.0
    out[AUX + 5] = float(board.halfmove_clock)
    # AUX + 6 stays zero.
    out[AUX + 7] = 1.0

    return out.reshape(1, 112, 8, 8)


def bits(mask: int, mirror: bool) -> np.ndarray:
    """Bit n means rank n//8, file n%8; mirroring for Black flips ranks only."""
    plane = np.zeros(64, dtype=np.float32)
    while mask:
        bit = (mask & -mask).bit_length() - 1
        idx = (bit ^ 56) if mirror else bit     # xor 56 flips the rank nibble
        plane[idx] = 1.0
        mask &= mask - 1
    return plane


GAMES = [
    [],
    ["e2e4"],
    ["e2e4", "e7e5"],
    ["e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6"],          # Ruy Lopez
    ["d2d4", "g8f6", "c2c4", "e7e6", "g1f3", "d7d5", "b1c3", "f8e7", "c1g5", "e8g8"],
    ["e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6"],
    ["g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6"],          # repetition
]


def compare() -> int:
    from lczerolens import LczeroBoard

    failures = 0
    for moves in GAMES:
        mine_board = chess.Board()
        ref_board = LczeroBoard()
        for uci in moves:
            mine_board.push_uci(uci)
            ref_board.push_uci(uci)

        mine = encode(mine_board)
        ref = ref_board.to_input_tensor(with_history=True).numpy().reshape(1, 112, 8, 8)

        if np.allclose(mine, ref):
            print(f"  ok    {len(moves):2d} plies  {' '.join(moves) or '(start)'}")
        else:
            failures += 1
            diff = np.argwhere(~np.isclose(mine, ref))
            planes = sorted({int(d[1]) for d in diff})
            print(f"  FAIL  {len(moves):2d} plies  {' '.join(moves) or '(start)'}")
            print(f"        {len(diff)} cells differ, planes {planes}")
            for p in planes[:4]:
                print(f"        plane {p}: mine={mine[0, p].ravel().tolist()}")
                print(f"        plane {p}:  lc0={ref[0, p].ravel().tolist()}")
    return failures


def play() -> None:
    import onnxruntime as ort

    index = POLICY_INDEX.read_text().split()
    model = MODELS / "maia-1900.onnx"
    if not model.exists():
        print(f"\n{model.name} missing -- run tools/export_maia.py first")
        return

    session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
    print(f"\nmaia-1900 top moves  (inputs {[i.name for i in session.get_inputs()]}, "
          f"outputs {[o.name for o in session.get_outputs()]})")

    for moves in GAMES[:5]:
        board = chess.Board()
        for uci in moves:
            board.push_uci(uci)
        policy = session.run(["/output/policy"], {"/input/planes": encode(board)})[0][0]

        scored = []
        for move in board.legal_moves:
            slot = slot_for(index, move, board.turn)
            if slot >= 0:
                scored.append((policy[slot], board.san(move)))
        scored.sort(reverse=True)
        top = ", ".join(f"{san} {p:.2f}" for p, san in scored[:5])
        print(f"  {' '.join(moves) or '(start)':44} -> {top}")


def slot_for(index: list[str], move: chess.Move, turn: bool) -> int:
    """Mirror of PolicyIndex.slotFor: mover's frame, no suffix for knight promotions."""
    def sq(s: int) -> str:
        if turn == chess.BLACK:
            s ^= 56
        return chess.square_name(s)

    suffix = {chess.QUEEN: "q", chess.ROOK: "r", chess.BISHOP: "b"}.get(move.promotion, "")
    key = sq(move.from_square) + sq(move.to_square) + suffix
    try:
        return index.index(key)
    except ValueError:
        return -1


if __name__ == "__main__":
    print("Encoder vs lc0")
    failed = compare()
    play()
    print(f"\n{'FAILED: ' + str(failed) + ' position(s) differ' if failed else 'All positions match lc0'}")
    sys.exit(1 if failed else 0)
