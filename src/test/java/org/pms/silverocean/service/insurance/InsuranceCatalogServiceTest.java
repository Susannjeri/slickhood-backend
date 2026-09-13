package org.pms.silverocean.service.insurance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.InsuranceAgencyRepo;
import org.pms.silverocean.database.pms.InsuranceCompanyRepo;
import org.pms.silverocean.database.pms.InsurancePaymentConfigurationRepo;
import org.pms.silverocean.database.pms.entities.InsuranceAgency;
import org.pms.silverocean.database.pms.entities.InsuranceCompany;
import org.pms.silverocean.service.PMSCustomException;
import org.pms.silverocean.service.account.AccountService;
import org.pms.silverocean.service.account.dao.AccountDao;
import org.pms.silverocean.service.auth.dao.UserDao;
import org.pms.silverocean.service.filestorage.GarageService;
import org.pms.silverocean.service.filestorage.UploadMalwarePolicy;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsuranceCatalogServiceTest {
    @Mock InsuranceCompanyRepo companies;
    @Mock InsuranceAgencyRepo agencies;
    @Mock InsurancePaymentConfigurationRepo configurations;
    @Mock AccountDao accounts;
    @Mock AccountService accountService;
    @Mock UserDao users;
    @Mock GarageService garageService;
    @Mock UploadMalwarePolicy malwarePolicy;
    @InjectMocks InsuranceService service;

    private InsuranceCompany apa;

    @BeforeEach
    void setUp() {
        apa = new InsuranceCompany();
        apa.setId(8L);
        apa.setAgencyId(1L);
        apa.setCode("APA");
        apa.setName("APA Insurance");
        apa.setActive(true);
    }

    @Test
    void publicCatalogueIncludesOnlyRepositoryApprovedActivePartnersAndBranding() {
        apa.setLogoUrl("/insurance/brands/apa.webp");
        when(companies.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(apa));

        assertThat(service.companies()).containsExactly(
                new InsuranceModels.CompanyView(8L,"APA","APA Insurance","/insurance/brands/apa.webp",null,true));
    }

    @Test
    void managerCannotPublishAnInsecureLogoUrl() {
        when(companies.findByCodeIgnoreCase("APA")).thenReturn(Optional.of(apa));
        var request = new InsuranceModels.CompanyUpdateRequest("APA Insurance","http://unsafe.example/logo.png",
                null,null,null,null,true);

        assertThatThrownBy(() -> service.updateCompany("APA",request)).isInstanceOf(PMSCustomException.class);
        verify(companies,never()).save(any());
    }

    @Test
    void managerCanAddAnApprovedPartnerUnderSilverwood() {
        InsuranceAgency agency = new InsuranceAgency();
        agency.setId(3L);
        when(companies.findByCodeIgnoreCase("PIONEER")).thenReturn(Optional.empty());
        when(agencies.findByCodeAndActiveTrue("SILVERWOOD")).thenReturn(Optional.of(agency));
        when(users.getUserId()).thenReturn(19L);
        when(companies.save(any())).thenAnswer(invocation -> {
            InsuranceCompany saved = invocation.getArgument(0);
            saved.setId(12L);
            return saved;
        });

        var result = service.createCompany(new InsuranceModels.CompanyCreateRequest("PIONEER","Pioneer Insurance",
                "/insurance/brands/pioneer.webp","Approved partner",null,null,null));

        assertThat(result.code()).isEqualTo("PIONEER");
        assertThat(result.logoUrl()).isEqualTo("/insurance/brands/pioneer.webp");
        assertThat(result.active()).isTrue();
        verify(companies).save(any(InsuranceCompany.class));
    }

    @Test
    void managerCanUploadAValidatedPrivatePartnerLogo() throws Exception {
        byte[] png = new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a};
        when(companies.findByCodeIgnoreCase("APA")).thenReturn(Optional.of(apa));
        when(companies.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(garageService.getPresignedUrlForStoredObject("insurance/companies/8/logo.png"))
                .thenReturn("https://files.example/apa-logo");

        var result = service.uploadCompanyLogo("APA", new MockMultipartFile("file","apa.png","image/png",png));

        assertThat(result.logoUrl()).isEqualTo("https://files.example/apa-logo");
        assertThat(apa.getLogoFileRef()).isEqualTo("insurance/companies/8/logo.png");
        verify(malwarePolicy).requireSafe(png);
        verify(garageService).uploadBytes("insurance/companies/8/logo.png",png,"image/png");
    }
}
