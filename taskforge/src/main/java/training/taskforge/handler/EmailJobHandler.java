package training.taskforge.handler;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import training.taskforge.error.JobExecutionException;
import training.taskforge.model.Job;
import training.taskforge.model.JobResult;

import java.io.IOException;
import java.time.Duration;

public class EmailJobHandler extends AbstractSimulatedHandler {
    @Override
    public JobResult handle(Job job) throws JobExecutionException {
        var start = System.currentTimeMillis();

        String recipient = job.payload().get("to");
        if (recipient == null || recipient.isBlank()) {
            throw new JobExecutionException.HandlerCrashed("missing recipient");
        }

        String subject = job.payload().getOrDefault("subject", "Test Email");
        String body = job.payload().getOrDefault("body", "Test email body");

        try {
            sendEmail(recipient, subject, body);
        } catch (IOException e) {
            return new JobResult.Failure(job.id(), Duration.ofMillis(System.currentTimeMillis() - start), "failed to send email: " + e.getMessage());
        }

        return new JobResult.Success(
                job.id(),
                Duration.ofMillis(System.currentTimeMillis() - start),
                "sent to " + recipient);
    }

    private void sendEmail(String recipient, String subject, String body) throws IOException {
        Email from = new Email("test@lng-app.com");
        Email to = new Email(recipient);
        Content content = new Content("text/plain", body);
        Mail mail = new Mail(from, subject, to, content);

        SendGrid sg = new SendGrid("");
        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
//            System.out.println(response.getStatusCode());
//            System.out.println(response.getBody());
//            System.out.println(response.getHeaders());
        } catch (IOException ex) {
            throw ex;
        }
    }
}