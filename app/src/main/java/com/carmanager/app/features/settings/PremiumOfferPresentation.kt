package com.carmanager.app.features.settings

import com.carmanager.app.core.domain.model.PremiumOffer

/** Prix localisé fourni par Play, sans calcul ni prix de secours. */
internal fun premiumDisplayPrice(offer: PremiumOffer?): String? = offer?.formattedPrice?.takeIf { it.isNotBlank() }
