// File:  NeuralNetwork.java
// Name:  Mo K. Eid (mohamedkeid@gmail.com)
// Desc:  A customizable neural network class built from scratch that makes use of batch gradient descent

import java.util.Random;

class NeuralNetwork {
    // Instance variables related to the structure of the neural network
    private int inputSize;
    private int[] hiddenLayerSizes;
    private int numClassifiers;

    // Matrix containing the weight vectors between each layer
    private double[][] weightsForAllLayers;

    // Instance variables related to optimization
    private double learningRate;
    private double regularizationRate;

    // Normalization parameter
    private double[] featureAverages;

    NeuralNetwork(int paramInputSize, int[] paramHiddenLayerSizes, int paramNumClassifiers) {
        inputSize = paramInputSize;
        hiddenLayerSizes = paramHiddenLayerSizes;
        numClassifiers = paramNumClassifiers;
        weightsForAllLayers = generateRandomWeights();
        learningRate = 0.3;
        regularizationRate = 10;
    }

    // Infer a function from labeled training data
    void train(int[][] trainingExamples, int[] trainingActual, int numIterations) {
        // Center the training data features
        calculateFeatureMeans(trainingExamples);
        double[][] normalizedExamples = normalizeFeatures(trainingExamples);

        int i = 0;
        while(i < numIterations) {
            System.out.println("Training Iteration: " + (i + 1) + " of " + numIterations);
            backPropagation(normalizedExamples, trainingActual);
            i++;
        }
    }

    // Return the rate the neural network predicts its own labeled training examples correctly
    double checkAccuracy(int[][] inputSet, int[] actualSet) {
        double[][] normalizedInputSet = normalizeFeatures(inputSet);
        double numCorrect = 0.0;

        for(int inputIndex = 1; inputIndex < inputSet.length - 1; inputIndex++) {
            double[] trainingExample = normalizedInputSet[inputIndex];
            double[][] predictions = predict(trainingExample);
            double[] predictedOutput = predictions[predictions.length - 1];
            int predictedClass = translatePrediction(predictedOutput);
            numCorrect += (predictedClass == actualSet[inputIndex]) ? 1 : 0;
        }

        return numCorrect / inputSet.length;
    }

    // Backward propagate errors in batch mode and update weights given labeled training data
    private void backPropagation(double[][] trainingExamples, int[] trainingActual) {
        // Initialize the delta accumulator
        double[][] gradient = new double[hiddenLayerSizes.length + 1][];

        // Learn from each training example
        for(int trainingExampleIndex = 0; trainingExampleIndex < trainingExamples.length - 1; trainingExampleIndex++) {
            double[][] predictions = predict(trainingExamples[trainingExampleIndex]);
            double[][] deltas = new double[weightsForAllLayers.length][];

            // Iterate through each weights layer
            for(int deltaLayerIndex = deltas.length - 1; deltaLayerIndex >= 0; deltaLayerIndex--) {
                boolean onOutputLayer = (deltaLayerIndex == deltas.length - 1);
                int currentLayerSize = onOutputLayer ? numClassifiers : hiddenLayerSizes[deltaLayerIndex] + 1;
                deltas[deltaLayerIndex] = new double[currentLayerSize];

                // Initialize the gradients for this layer if null
                if(gradient[deltaLayerIndex] == null)
                    gradient[deltaLayerIndex] = new double[weightsForAllLayers[deltaLayerIndex].length];

                // Calculate output deltas
                if(onOutputLayer) {
                    // Encode the class into a vector
                    int[] encodedActual = oneHotEncode(trainingActual[trainingExampleIndex]);

                    for(int nodeIndex = 0; nodeIndex < numClassifiers; nodeIndex++) {
                        double activationPrime = activatePrime(predictions[predictions.length - 1][nodeIndex]);
                        deltas[deltaLayerIndex][nodeIndex] = (predictions[predictions.length - 1][nodeIndex] - encodedActual[nodeIndex]) * activationPrime;
                    }
                }

                // Calculate hidden layer deltas
                else {
                    boolean nextLayerIsOutput = (deltaLayerIndex == deltas.length - 2);
                    int nextLayerSize = nextLayerIsOutput ? numClassifiers : hiddenLayerSizes[deltaLayerIndex + 1];

                    // Iterate through each activation node
                    for(int nodeIndex = 0; nodeIndex < currentLayerSize; nodeIndex++) {
                        double delta = 0.0;

                        // Iterate through each of the next layer's activation nodes
                        for(int nextNodeIndex = 0; nextNodeIndex < nextLayerSize; nextNodeIndex++) {
                            int weightIndex = (currentLayerSize * nextNodeIndex) + nodeIndex;
                            double weightVal = weightsForAllLayers[deltaLayerIndex + 1][weightIndex];
                            double activationPrime = activatePrime(predictions[deltaLayerIndex + 1][nextNodeIndex]);
                            delta += weightVal * deltas[deltaLayerIndex + 1][nextNodeIndex] * activationPrime;
                        }

                        deltas[deltaLayerIndex][nodeIndex] = delta;
                    }
                }
            }

            // Accumulate deltas
            for(int deltaLayerIndex = deltas.length - 1; deltaLayerIndex >= 0; deltaLayerIndex--) {
                // Get parameters on the current layer
                boolean onOutputLayer = (deltaLayerIndex == deltas.length - 1);
                int currentLayerSize = onOutputLayer ? numClassifiers : hiddenLayerSizes[deltaLayerIndex];

                // Get parameters on the previous layer
                boolean previousIsInput = (deltaLayerIndex == 0);
                int previousLayerSize = previousIsInput ? inputSize : hiddenLayerSizes[deltaLayerIndex - 1] + 1;

                // Use each weight connection to calculate the gradient
                for(int previousNodeIndex = 0; previousNodeIndex < previousLayerSize; previousNodeIndex++) {
                    for(int currentNodeIndex = 0; currentNodeIndex < currentLayerSize; currentNodeIndex++) {
                        int weightIndex = (currentNodeIndex * previousLayerSize) + previousNodeIndex;
                        double previousActivation;

                        // Set activation to be 1 if the previous node is a bias
                        if(previousNodeIndex == previousLayerSize - 1)
                            previousActivation = 1;
                        else
                            previousActivation = previousIsInput ? trainingExamples[trainingExampleIndex][previousNodeIndex] : predictions[deltaLayerIndex - 1][previousNodeIndex];

                        gradient[deltaLayerIndex][weightIndex] += deltas[deltaLayerIndex][currentNodeIndex] * previousActivation;
                    }
                }
            }
        }

        // Update weights
        for(int deltaLayerIndex = 0; deltaLayerIndex < gradient.length; deltaLayerIndex++) {
            boolean onInputLayer = (deltaLayerIndex == 0);
            int currentLayerSize = onInputLayer ? inputSize : hiddenLayerSizes[deltaLayerIndex - 1] + 1;
            boolean nextIsOutLayer = (deltaLayerIndex == gradient.length - 1);
            int nextLayerSize = nextIsOutLayer ? numClassifiers : hiddenLayerSizes[deltaLayerIndex];

            for(int nextNodeIndex = 0; nextNodeIndex < nextLayerSize; nextNodeIndex++) {
                for(int currentNodeIndex = 0; currentNodeIndex < currentLayerSize; currentNodeIndex++) {
                    int weightIndex = (nextNodeIndex * currentLayerSize) + currentNodeIndex;

                    // Introduce regularization to prevent overfitting
                    if(currentNodeIndex != currentLayerSize - 1 && !onInputLayer)
                        weightsForAllLayers[deltaLayerIndex][weightIndex] = weightsForAllLayers[deltaLayerIndex][weightIndex]  * (1 - learningRate * regularizationRate / trainingExamples.length) -
                                gradient[deltaLayerIndex][weightIndex] * learningRate / trainingExamples.length;
                    else
                        weightsForAllLayers[deltaLayerIndex][weightIndex] -= gradient[deltaLayerIndex][weightIndex] * learningRate / trainingExamples.length;
                }
            }
        }
    }

    // Generate a random matrix of weight values between min and mix based on the neural network's structure
    private double[][] generateRandomWeights() {
        // There will be a layer of weights between the layers of the neural network
        double[][] weights = new double[hiddenLayerSizes.length + 1][];

        // The range of weight randomization
        double min = -1.0;
        double max = 1.0;

        // Iterate through each layer of nodes
        for(int weightLayerIndex = 0; weightLayerIndex < weights.length; weightLayerIndex++) {
            boolean forHiddenLayer = (weightLayerIndex != weights.length - 1);
            int currentLayerSize = forHiddenLayer ? hiddenLayerSizes[weightLayerIndex] : numClassifiers;

            boolean previousIsInputLayer = (weightLayerIndex == 0);
            int previousLayerSize = previousIsInputLayer ? inputSize : hiddenLayerSizes[weightLayerIndex - 1] + 1;

            // If the previous layer is a hidden one, allocate room for the bias node that will be added later
            int weightLayerSize = currentLayerSize * previousLayerSize;
            weights[weightLayerIndex] = new double[weightLayerSize];

            // Set each weight's value to be between min and max
            for(int weightIndex = 0; weightIndex < weightLayerSize; weightIndex++) {
                Random random = new Random();
                double randomValue = min + (max - min) * random.nextDouble();
                weights[weightLayerIndex][weightIndex] = randomValue;
            }
        }

        return weights;
    }

    // Sigmoid function
    private double activate(double val) {
        return (1 / (1 + Math.exp(-val)));
    }

    // Derivative of the sigmoid function
    private double activatePrime(double val) {
        return val * (1 - val);
    }

    private double[][] normalizeFeatures(int[][] set) {
        double[][] normalizedSet = new double[set.length][];

        for(int exampleIndex = 0; exampleIndex < normalizedSet.length; exampleIndex++) {
            normalizedSet[exampleIndex] = new double[inputSize];

            for(int featureIndex = 0; featureIndex < inputSize; featureIndex++) {
                double centeredFeature = set[exampleIndex][featureIndex] - featureAverages[featureIndex];
                normalizedSet[exampleIndex][featureIndex] = centeredFeature;
            }
        }

        return normalizedSet;
    }

    // Iterate through every example in a set and calculate the mean for each feature
    private void calculateFeatureMeans(int[][] set) {
        // Calculate the means
        featureAverages = new double[inputSize];
        for(int featureIndex = 0; featureIndex < inputSize; featureIndex++) {
            for(int exampleIndex = 0; exampleIndex < set.length; exampleIndex++)
                featureAverages[featureIndex] += set[exampleIndex][featureIndex];

            featureAverages[featureIndex] /= set.length;
        }
    }

    // Encode classes (i.e., 2 -> [0, 1, 0, .., n])
    private int[] oneHotEncode(int val) {
        int[] encodedVal = new int[numClassifiers];
        for(int i = 0; i < numClassifiers; i++)
            encodedVal[i] = (i == val) ? 1 : 0;
        return encodedVal;
    }

    // Make prediction based on its training
    private double[][] predict(double[] input) {
        double[][] predictions = new double[this.hiddenLayerSizes.length + 1][];

        for(int actLayerIndex = 0; actLayerIndex < predictions.length; actLayerIndex++) {
            boolean onOutputLayer = (actLayerIndex == predictions.length - 1);
            boolean previousIsInput = (actLayerIndex == 0);

            // Compute layer sizes (the + 1 is for the bias node)
            int previousLayerSize = previousIsInput ? inputSize : (hiddenLayerSizes[actLayerIndex - 1] + 1);
            int currentLayerSize = onOutputLayer ? numClassifiers : (hiddenLayerSizes[actLayerIndex]);
            boolean shouldAddBias = !onOutputLayer;
            predictions[actLayerIndex] = new double[currentLayerSize + (shouldAddBias ? 1 : 0)];

            // Compute activations for each node in the current layer
            for(int actNodeIndex = 0; actNodeIndex < currentLayerSize; actNodeIndex++) {
                // Sum the connections between the appropriate nodes in the current layer and the previous layer
                double sum = 0.0;
                for(int previousNodeIndex = 0; previousNodeIndex < previousLayerSize; previousNodeIndex++) {
                    int weightIndex = (previousLayerSize * actNodeIndex) + previousNodeIndex;
                    double weightVal = weightsForAllLayers[actLayerIndex][weightIndex];
                    double previousNodeVal = previousIsInput ? input[previousNodeIndex] : predictions[actLayerIndex - 1][previousNodeIndex];
                    sum += weightVal * previousNodeVal;
                }

                // Activate the summed value and assign it in the predictions matrix
                double activation = activate(sum);
                predictions[actLayerIndex][actNodeIndex] = activation;
            }

            // Add bias if needed
            if(shouldAddBias) {
                int biasIndex = hiddenLayerSizes[actLayerIndex];
                predictions[actLayerIndex][biasIndex] = 1;
            }
        }

        return predictions;
    }

    // Decode class (i.e., [0, 1, 0, .., n] -> 2)
    private int translatePrediction(double[] encodedVal) {
        int predictedClass = 0;

        for(int classifierIndex = 0; classifierIndex < numClassifiers; classifierIndex++)
            if(encodedVal[classifierIndex] > encodedVal[predictedClass])
                predictedClass = classifierIndex;

        return predictedClass;
    }
}