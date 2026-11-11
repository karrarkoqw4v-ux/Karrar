FROM pytorch/pytorch:2.4.0-cuda12.1-cudnn9-runtime
RUN apt-get update && apt-get install -y --no-install-recommends nodejs make && rm -rf /var/lib/apt/lists/*
WORKDIR /bawsala
COPY requirements.txt .
RUN pip install -r requirements.txt tokenizers datasets
COPY . .
EXPOSE 8000
CMD ["make", "serve"]
