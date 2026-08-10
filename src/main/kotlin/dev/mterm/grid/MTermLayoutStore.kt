package dev.mterm.grid

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "MTermLayout", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class MTermLayoutStore : PersistentStateComponent<MTermLayoutStore.State> {

    class State {
        var agentIds: MutableList<String> = mutableListOf()
        var columns: Int = 0
        var colWeights: MutableList<Int> = mutableListOf()
        var rowWeights: MutableList<Int> = mutableListOf()
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
    }

    val agentIds: List<String> get() = state.agentIds.toList()

    val columns: Int? get() = state.columns.takeIf { it in 1..MAX_COLUMNS }

    fun columnFractions(): DoubleArray = state.colWeights.toFractions()

    fun rowFractions(): DoubleArray = state.rowWeights.toFractions()

    fun save(agentIds: List<String>, columns: Int?, colFractions: DoubleArray, rowFractions: DoubleArray) {
        state.agentIds = agentIds.toMutableList()
        state.columns = columns ?: 0
        state.colWeights = colFractions.toWeights()
        state.rowWeights = rowFractions.toWeights()
    }

    private fun List<Int>.toFractions(): DoubleArray {
        val total = sumOf { it }
        if (isEmpty() || total <= 0) return DoubleArray(0)
        return DoubleArray(size) { this[it].toDouble() / total }
    }

    private fun DoubleArray.toWeights(): MutableList<Int> =
        map { (it * SCALE).toInt().coerceAtLeast(1) }.toMutableList()

    companion object {
        private const val SCALE = 1000
        const val MAX_COLUMNS = 6

        fun getInstance(project: Project): MTermLayoutStore = project.service()
    }
}
