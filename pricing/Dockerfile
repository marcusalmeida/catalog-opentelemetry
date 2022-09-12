FROM python:3.10.7-slim

COPY requirements.in requirements.in

RUN pip install pip-tools && pip-compile 
RUN mount=type=cache,target=/root/.cache && pip install -r requirements.in

COPY app.py app.py

EXPOSE 5000
ENTRYPOINT ["opentelemetry-instrument", "flask", "run", "--host=0.0.0.0"]
