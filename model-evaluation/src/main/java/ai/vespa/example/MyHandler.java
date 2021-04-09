package ai.vespa.example;

import ai.vespa.models.evaluation.ModelsEvaluator;
import ai.vespa.models.evaluation.FunctionEvaluator;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.JsonFormat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;


public class MyHandler extends LoggingRequestHandler {

    private final ModelsEvaluator modelsEvaluator;

    public MyHandler(ModelsEvaluator modelsEvaluator, Context context) {
        super(context);
        this.modelsEvaluator = modelsEvaluator;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        String model = request.getProperty("model");
        String function = request.getProperty("function");

        // Create evaluator
        FunctionEvaluator evaluator = modelsEvaluator.evaluatorOf(model, function);

        // Bind input arguments
        String argumentName = request.getProperty("argumentName");
        String argumentValue = request.getProperty("argumentValue");
        if (argumentName != null && argumentValue != null) {
            for (Map.Entry<String, TensorType> argumentType : evaluator.function().argumentTypes().entrySet()) {
                if (argumentName.equals(argumentType.getKey())) {
                    evaluator.bind(argumentName, Tensor.from(argumentType.getValue(), argumentValue));
                }
            }
        }

        // Evaluate model
        Tensor result = evaluator.evaluate();

        // Return result
        return new RawResponse(JsonFormat.encode(result));
    }

    private static class RawResponse extends HttpResponse {

        private final byte[] data;

        RawResponse(byte[] data) {
            super(200);
            this.data = data;
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write(data);
        }
    }

}
