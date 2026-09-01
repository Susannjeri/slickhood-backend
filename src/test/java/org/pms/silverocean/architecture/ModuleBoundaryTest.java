package org.pms.silverocean.architecture;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleBoundaryTest {
    private static final Path SOURCE=Path.of("src/main/java/org/pms/silverocean");
    @Test void controllersDoNotAccessRepositories()throws IOException{assertNoJavaSourceContains(SOURCE.resolve("controller"),"import org.pms.silverocean.database.pms.","Repo;");}
    @Test void paymentDoesNotDependOnSubscriptionImplementation()throws IOException{assertNoJavaSourceContains(SOURCE.resolve("service/payment"),"org.pms.silverocean.service.subscription");}
    @Test void paymentDoesNotDependOnRoleService()throws IOException{assertNoJavaSourceContains(SOURCE.resolve("service/payment"),"org.pms.silverocean.service.auth.roles.RoleService");}
    @Test void runtimeDirectoryDoesNotRequireAnEmbeddedBuildProperty()throws IOException{assertNoJavaSourceContains(SOURCE,"@Value(\"${silverocean.dir}\")");}
    private void assertNoJavaSourceContains(Path root,String...needles)throws IOException{try(var paths=Files.walk(root)){List<String> violations=paths.filter(p->p.toString().endsWith(".java")).filter(p->{try{String source=Files.readString(p);for(String needle:needles)if(!source.contains(needle))return false;return true;}catch(IOException e){throw new RuntimeException(e);}}).map(Path::toString).toList();assertTrue(violations.isEmpty(),"Module boundary violations: "+violations);}}
}
