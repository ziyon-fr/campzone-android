package fr.ziyon.campzone.core.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface StringProvider {
    fun get(@StringRes id: Int, vararg args: Any): String
    fun getQuantity(@PluralsRes id: Int, quantity: Int, vararg args: Any): String
}

@Singleton
class AndroidStringProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : StringProvider {
    override fun get(id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

    override fun getQuantity(id: Int, quantity: Int, vararg args: Any): String =
        if (args.isEmpty()) {
            context.resources.getQuantityString(id, quantity)
        } else {
            context.resources.getQuantityString(id, quantity, *args)
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StringProviderModule {
    @Binds
    abstract fun bindStringProvider(provider: AndroidStringProvider): StringProvider
}
