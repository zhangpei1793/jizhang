package com.example.ledger.data.repository

import com.example.ledger.data.model.Category
import com.example.ledger.data.model.CategoryStat
import com.example.ledger.data.model.DailyStat
import com.example.ledger.data.model.MonthStats
import com.example.ledger.data.model.Record
import com.example.ledger.data.model.TxType
import com.example.ledger.util.DateUtils
import com.example.ledger.util.Money
import java.time.YearMonth

/**
 * 统计仓库：所有聚合逻辑集中在此，UI 只负责渲染。
 */
class StatsRepository(
    private val categoryRepository: CategoryRepository,
    private val recordRepository: RecordRepository
) {

    suspend fun loadMonth(year: Int, month: Int): Result<MonthStats> {
        val categories = categoryRepository.fetchAll().getOrElse { return Result.failure(it) }
        val records = recordRepository.fetchByMonth(year, month).getOrElse { return Result.failure(it) }
        return Result.success(aggregate(year, month, records, categories))
    }

    private fun aggregate(
        year: Int,
        month: Int,
        records: List<Record>,
        categories: List<Category>
    ): MonthStats {
        val categoryMap = categories.associateBy { it.id }
        val incomeRecords = records.filter { it.type == TxType.INCOME }
        val expenseRecords = records.filter { it.type == TxType.EXPENSE }
        val income = Money.sum(incomeRecords.map { it.amount })
        val expense = Money.sum(expenseRecords.map { it.amount })

        // 按日期分组后逐日汇总，避免对每一天都全量遍历
        val recordsByDay = records.groupBy { record ->
            DateUtils.parseDate(record.recordDate)?.dayOfMonth ?: 0
        }
        val dayCount = YearMonth.of(year, month).lengthOfMonth()
        val daily = (1..dayCount).map { day ->
            val dayRecords = recordsByDay[day].orEmpty()
            DailyStat(
                day = day,
                income = Money.sum(dayRecords.filter { it.type == TxType.INCOME }.map { it.amount }),
                expense = Money.sum(dayRecords.filter { it.type == TxType.EXPENSE }.map { it.amount })
            )
        }

        return MonthStats(
            year = year,
            month = month,
            income = income,
            expense = expense,
            expenseByCategory = buildCategoryStats(expenseRecords, categoryMap),
            incomeByCategory = buildCategoryStats(incomeRecords, categoryMap),
            daily = daily,
            records = records,
            categories = categoryMap
        )
    }

    private fun buildCategoryStats(
        records: List<Record>,
        categories: Map<String, Category>
    ): List<CategoryStat> {
        val total = Money.sum(records.map { it.amount })
        return records
            .groupBy { it.categoryId }
            .map { (categoryId, list) ->
                val amount = Money.sum(list.map { it.amount })
                val category = categories[categoryId]
                CategoryStat(
                    categoryId = categoryId,
                    name = category?.name.orEmpty(),
                    iconKey = category?.iconKey ?: "other",
                    amount = amount,
                    percent = Money.percent(amount, total)
                )
            }
            .sortedByDescending { it.amount }
    }
}
