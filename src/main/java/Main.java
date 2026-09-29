import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

public class Main {
    public static void main(String[] args) {

        JUnitCore runner = new JUnitCore();

        runner.addListener(new RunListener() {
            @Override
            public void testStarted(Description description) {
                System.out.println("Executando: " + description.getMethodName());
            }

            @Override
            public void testFinished(Description description) {
                System.out.println("Concluido: " + description.getMethodName() + "\n");
            }

            @Override
            public void testFailure(Failure failure) {
                System.out.println("Falhou: " + failure.getMessage() + "\n");
            }
        });

        Result resultado = runner.run(AdministradorTeste.class);

        System.out.println("Total executados: " + resultado.getRunCount());
        System.out.println("Sucessos: " + (resultado.getRunCount() - resultado.getFailureCount()));
        System.out.println("Falhas: " + resultado.getFailureCount());
    }
}