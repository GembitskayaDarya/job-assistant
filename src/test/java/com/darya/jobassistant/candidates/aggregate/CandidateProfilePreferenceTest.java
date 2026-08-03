package com.darya.jobassistant.candidates.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.PreferenceImportance;
import org.junit.jupiter.api.Test;

class CandidateProfilePreferenceTest {

    @Test
    void constructor_validWeightedPreference_isCreated() {
        CandidateProfilePreference preference =
                new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", PreferenceImportance.PREFERRED);

        assertThat(preference.type()).isEqualTo(CandidatePreferenceType.COMPANY_TYPE);
        assertThat(preference.value()).isEqualTo("Product");
        assertThat(preference.importance()).isEqualTo(PreferenceImportance.PREFERRED);
    }

    @Test
    void constructor_allowedWorkCountry_withNullImportance_isCreated() {
        CandidateProfilePreference preference =
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null);

        assertThat(preference.importance()).isNull();
    }

    @Test
    void constructor_allowedWorkCountry_withNonNullImportance_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(
                CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", PreferenceImportance.NEUTRAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_workArrangement_withNullImportance_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(CandidatePreferenceType.WORK_ARRANGEMENT, "Remote", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_companyType_withNullImportance_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(CandidatePreferenceType.COMPANY_TYPE, "Product", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_contractType_withNullImportance_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullType_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(null, "Remote", PreferenceImportance.STRONG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_blankValue_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(
                CandidatePreferenceType.WORK_ARRANGEMENT, "   ", PreferenceImportance.STRONG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_valueIsTrimmed() {
        CandidateProfilePreference preference =
                new CandidateProfilePreference(CandidatePreferenceType.WORK_ARRANGEMENT, "  Remote  ", PreferenceImportance.STRONG);

        assertThat(preference.value()).isEqualTo("Remote");
    }

    // ---- priorityOrder (Sprint 9 Step 3 correction) ----

    @Test
    void constructor_orderSignificantType_withPriorityOrder_isCreated() {
        CandidateProfilePreference preference =
                new CandidateProfilePreference(CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, 0);

        assertThat(preference.priorityOrder()).isZero();
    }

    @Test
    void constructor_orderSignificantType_withoutPriorityOrder_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(
                CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_threeArgConvenienceConstructor_forOrderSignificantType_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(
                CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_notOrderSignificantType_withPriorityOrder_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(
                CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativePriorityOrder_isRejected() {
        assertThatThrownBy(() -> new CandidateProfilePreference(
                CandidatePreferenceType.CONTRACT_TYPE, "B2B", PreferenceImportance.PREFERRED, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_threeArgConvenienceConstructor_forOrderInsignificantType_leavesPriorityOrderNull() {
        CandidateProfilePreference preference =
                new CandidateProfilePreference(CandidatePreferenceType.ALLOWED_WORK_COUNTRY, "Poland", null);

        assertThat(preference.priorityOrder()).isNull();
    }
}
