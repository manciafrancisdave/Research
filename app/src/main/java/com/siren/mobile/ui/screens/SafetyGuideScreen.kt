package com.siren.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.siren.mobile.R
import com.siren.mobile.ui.components.InfoBanner
import com.siren.mobile.ui.components.SectionLabel
import com.siren.mobile.ui.theme.Ink
import com.siren.mobile.ui.theme.InkSubtle
import com.siren.mobile.ui.theme.Layout
import com.siren.mobile.ui.theme.SirenBlue
import com.siren.mobile.ui.theme.Space
import com.siren.mobile.ui.theme.Surface
import com.siren.mobile.ui.theme.SurfaceTint

data class GuideItem(val iconRes: Int, val title: String, val body: String)
data class GuideSection(val title: String, val items: List<GuideItem>)

/**
 * Carried over from the shipped APK — the 28 ic_sg_* pictograms were recovered from it
 * and are reused here unchanged.
 */
private val guideSections = listOf(
    GuideSection(
        "During the shaking",
        listOf(
            GuideItem(R.drawable.ic_sg_drop_cover_hold, "Drop, cover, hold on", "Get under a sturdy desk, cover your head and neck, and hold on until the shaking stops."),
            GuideItem(R.drawable.ic_sg_home_person, "Stay where you are", "Do not run outside mid-shake. Most injuries happen while moving."),
            GuideItem(R.drawable.ic_sg_shelter, "Find hard cover", "If there is no desk, crouch against an interior wall away from windows."),
            GuideItem(R.drawable.ic_sg_hazard, "Watch for falling objects", "Move clear of shelves, glass, and anything mounted overhead."),
        ),
    ),
    GuideSection(
        "Evacuating",
        listOf(
            GuideItem(R.drawable.ic_sg_evacuate_run, "Leave calmly", "Once shaking stops, walk quickly — do not run or push."),
            GuideItem(R.drawable.ic_sg_evac_route, "Follow the marked route", "Use the posted evacuation path, not shortcuts."),
            GuideItem(R.drawable.ic_sg_fire_exit, "Use stairs, never lifts", "Aftershocks can trap you in an elevator."),
            GuideItem(R.drawable.ic_sg_assembly_point, "Go to the assembly point", "Stay there so your adviser can complete the roll call."),
        ),
    ),
    GuideSection(
        "Getting help",
        listOf(
            GuideItem(R.drawable.ic_sg_call_sos, "Send an SOS", "Tap I Need Help in the app — it reaches your adviser and guardians at once."),
            GuideItem(R.drawable.ic_sg_call_signal, "If there is no signal", "SMS fallback still delivers to your saved emergency contacts."),
            GuideItem(R.drawable.ic_sg_megaphone, "Make noise if trapped", "Shout or tap on pipes in bursts of three. Conserve your voice."),
            GuideItem(R.drawable.ic_sg_lifering, "Help only if it is safe", "Never enter a damaged structure to reach someone."),
        ),
    ),
    GuideSection(
        "First aid",
        listOf(
            GuideItem(R.drawable.ic_sg_first_aid, "Treat bleeding first", "Apply firm direct pressure with a clean cloth."),
            GuideItem(R.drawable.ic_sg_aed_heart, "AED and CPR", "If someone is unresponsive and not breathing, start CPR and send for the AED."),
            GuideItem(R.drawable.ic_sg_stretcher, "Do not move the injured", "Unless there is immediate danger, wait for trained responders."),
            GuideItem(R.drawable.ic_sg_care_hands, "Keep them warm and calm", "Shock is common. Reassure and cover them."),
        ),
    ),
    GuideSection(
        "Fire and utilities",
        listOf(
            GuideItem(R.drawable.ic_sg_extinguisher, "Use an extinguisher", "Pull, aim at the base, squeeze, sweep — only on small fires."),
            GuideItem(R.drawable.ic_sg_extinguisher_hose, "Know the type", "Never use water on an electrical or oil fire."),
            GuideItem(R.drawable.ic_sg_hose_reel, "Fire hose reel", "For trained staff only, once the area is clear of people."),
            GuideItem(R.drawable.ic_sg_valve_shutoff, "Shut the main valve", "Close gas and water mains if you were taught how."),
            GuideItem(R.drawable.ic_sg_gas_cylinder, "Secure gas cylinders", "Move them upright and away from heat if it is safe."),
            GuideItem(R.drawable.ic_sg_pipe_leak, "Report leaks", "Do not use switches or flames near a suspected gas leak."),
            GuideItem(R.drawable.ic_sg_detector, "Heed detectors", "Treat every smoke or gas alarm as real."),
        ),
    ),
    GuideSection(
        "Hazards to avoid",
        listOf(
            GuideItem(R.drawable.ic_sg_hazard_alt, "Damaged structures", "Stay out of buildings with cracks, tilting, or fallen sections."),
            GuideItem(R.drawable.ic_sg_biohazard, "Biological spills", "Report and keep everyone well back."),
            GuideItem(R.drawable.ic_sg_chem_banned, "Chemical spills", "Do not attempt clean-up without protection and training."),
            GuideItem(R.drawable.ic_sg_flask, "Laboratory areas", "Broken glassware and reagents are a common post-quake hazard."),
            GuideItem(R.drawable.ic_sg_whirlwind, "Dust and debris", "Cover your mouth and nose while moving through it."),
        ),
    ),
)

@Composable
fun SafetyGuideScreen(onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Layout.screenPadding,
            end = Layout.screenPadding,
            bottom = Space.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        item { ScreenHeader(title = "Safety guide", onBack = onBack) }

        item {
            InfoBanner(
                "A supplementary local reference. It does not replace official PHIVOLCS warnings or your school's drill instructions.",
                Icons.Filled.Info,
            )
        }

        guideSections.forEach { section ->
            item { SectionLabel(section.title) }
            items(section.items, key = { it.iconRes }) { item -> GuideRow(item) }
        }
    }
}

@Composable
private fun GuideRow(item: GuideItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Layout.card))
            .background(Surface)
            .padding(Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(Layout.tile))
                .background(SurfaceTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                tint = SirenBlue,
                modifier = Modifier.size(26.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(item.body, style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }
    }
}
