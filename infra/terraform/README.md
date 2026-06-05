# ShortLinkOps Terraform

이 디렉토리는 ShortLinkOps dev 배포용 AWS 인프라를 생성합니다.

## 구성

- VPC: AWS default VPC
- Route53: Hosted Zone 신규 생성
- DNS Record: `fhwang.cloud`, `www.fhwang.cloud` A 레코드
- Public IP: Elastic IP
- AMI: Ubuntu 24.04 LTS x86_64
- Instance Type: `t3.small`
- Root Volume: 30GiB gp3 encrypted
- Security Group: 신규 생성
- Key Pair: Terraform에서 신규 생성
- Host Nginx: 80, 443 처리
- HTTPS: Let's Encrypt 인증서 자동 발급
- Provisioning: `user_data`로 Docker, Docker Compose, Git, Nginx, Certbot 설치

애플리케이션 배포는 루트의 `docker-compose.prod.yml`을 사용합니다.

## 네트워크

dev 배포는 default VPC에서 실행합니다. 단일 EC2 인스턴스에서 호스트 Nginx를 실행하고, Spring Boot, MySQL, Redis는 Docker Compose로 실행하는 구조이므로 별도 VPC와 subnet 구성을 만들지 않습니다.

보안그룹 이름은 `${project_name}-${environment}-sg` 형식입니다.

인바운드 규칙:

```text
22/tcp   0.0.0.0/0
80/tcp   0.0.0.0/0
443/tcp  0.0.0.0/0
```

Spring Boot 8080, MySQL 3306, Redis 6379는 Security Group에서 열지 않습니다. 운영 Compose의 Spring Boot 컨테이너는 EC2 내부 `127.0.0.1:8081`에만 바인딩합니다.

## 도메인

Terraform은 `domain_name` 값으로 Route53 Hosted Zone을 생성합니다. 기본값은 `fhwang.cloud`입니다.

생성되는 A 레코드:

```text
fhwang.cloud      -> Elastic IP
www.fhwang.cloud  -> Elastic IP
```

Terraform 적용 후 `hosted_zone_name_servers` 출력값을 도메인 등록기관의 name server로 설정합니다. NS 변경이 전파되면 도메인이 Route53 Hosted Zone을 바라봅니다. 같은 도메인의 Hosted Zone이 이미 있다면, 도메인 등록기관에서 어떤 NS를 바라보는지가 실제 사용 여부를 결정합니다.

## HTTPS

EC2 `user_data`는 호스트 OS에 Nginx와 Certbot을 설치합니다. 호스트 Nginx는 외부 80, 443 요청을 받고 Spring Boot 컨테이너의 `127.0.0.1:8081`로 프록시합니다.

Let's Encrypt 인증서 발급은 DNS 전파가 끝나야 성공합니다. 이를 위해 `shortlinkops-certbot.timer`가 DNS A 레코드가 Elastic IP를 바라볼 때까지 10분 간격으로 재시도합니다.

인증서 발급이 완료되면 Certbot이 Nginx 설정에 HTTP to HTTPS 리다이렉트를 적용합니다. 이후 `http://fhwang.cloud`, `http://www.fhwang.cloud` 요청은 HTTPS 주소로 이동합니다.

Let's Encrypt 기본 인증서 유효기간은 90일입니다. Certbot의 기본 갱신 타이머가 주기적으로 `certbot renew`를 실행하고, 인증서가 갱신 대상이 되면 자동으로 갱신합니다. 갱신 완료 후에는 `/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh`가 Nginx를 reload합니다.

인증서 발급 상태 확인:

```bash
sudo systemctl status shortlinkops-certbot.timer
sudo systemctl status certbot.timer
sudo journalctl -u shortlinkops-certbot.service -n 100
sudo tail -n 100 /var/log/shortlinkops-certbot.log
sudo certbot renew --dry-run
```

운영 환경의 `SHORTLINKOPS_BASE_URL` 값:

```text
https://www.fhwang.cloud
```

## EC2

Ubuntu AMI는 Canonical이 제공하는 AWS SSM Parameter Store의 최신 24.04 LTS x86_64 AMI ID를 사용합니다.

기본 인스턴스 유형은 `t3.small`입니다. Spring Boot, MySQL, Redis, Nginx를 한 인스턴스에서 모두 실행하므로 `t3.micro`는 메모리 부족이 발생할 수 있습니다.

루트 볼륨은 30GiB gp3입니다. Docker 이미지, Gradle 빌드 산출물, MySQL 데이터 볼륨, 로그 저장 공간을 고려한 기본값입니다.

## 키페어

Terraform이 EC2 키페어를 생성합니다. private key는 로컬의 `generated/` 디렉토리에 저장됩니다.

기본 경로:

```text
infra/terraform/generated/shortlinkops-key.pem
```

`generated/`와 `*.pem`은 Git에 포함하지 않습니다.

## AWS 인증

Terraform은 AWS Provider의 기본 인증 체인을 사용합니다. Access Key와 Secret Key는 `terraform.tfvars`에 작성하지 않습니다.

AWS CLI로 로그인한 계정 또는 프로필을 그대로 사용합니다.

로그인 상태를 확인합니다.

```bash
aws sts get-caller-identity
```

기본 프로필이 아닌 프로필을 사용한다면 Terraform 실행 전에 `AWS_PROFILE`을 지정합니다.

```bash
export AWS_PROFILE=shortlinkops
```

IAM Identity Center를 사용하는 경우에는 Terraform 실행 전에 AWS CLI 로그인을 완료합니다.

```bash
aws sso login --profile shortlinkops
export AWS_PROFILE=shortlinkops
```

## IAM 권한

Terraform 실행 주체에는 아래 권한이 필요합니다.

필요 권한:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:AllocateAddress",
        "ec2:AssociateAddress",
        "ec2:AuthorizeSecurityGroupEgress",
        "ec2:AuthorizeSecurityGroupIngress",
        "ec2:CreateSecurityGroup",
        "ec2:CreateTags",
        "ec2:DeleteKeyPair",
        "ec2:DeleteSecurityGroup",
        "ec2:DeleteTags",
        "ec2:DescribeAddresses",
        "ec2:DescribeImages",
        "ec2:DescribeInstanceStatus",
        "ec2:DescribeInstances",
        "ec2:DescribeKeyPairs",
        "ec2:DescribeNetworkInterfaces",
        "ec2:DescribeSecurityGroups",
        "ec2:DescribeSubnets",
        "ec2:DescribeVolumes",
        "ec2:DescribeVpcs",
        "ec2:DisassociateAddress",
        "ec2:ImportKeyPair",
        "ec2:ReleaseAddress",
        "ec2:RevokeSecurityGroupEgress",
        "ec2:RevokeSecurityGroupIngress",
        "ec2:RunInstances",
        "ec2:TerminateInstances",
        "route53:ChangeResourceRecordSets",
        "route53:ChangeTagsForResource",
        "route53:CreateHostedZone",
        "route53:DeleteHostedZone",
        "route53:GetChange",
        "route53:GetHostedZone",
        "route53:ListHostedZones",
        "route53:ListHostedZonesByName",
        "route53:ListResourceRecordSets",
        "route53:ListTagsForResource",
        "ssm:GetParameter"
      ],
      "Resource": "*"
    }
  ]
}
```

## 사용 방법

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
```

`terraform.tfvars` 값을 배포 환경에 맞게 수정합니다.

```hcl
aws_region       = "ap-northeast-2"
project_name     = "shortlinkops"
environment      = "dev"
domain_name      = "fhwang.cloud"
instance_type    = "t3.small"
key_pair_name    = "shortlinkops-key"
private_key_file = "generated/shortlinkops-key.pem"
ssh_allowed_cidr = "0.0.0.0/0"
root_volume_size = 30
certbot_email    = ""
```

Terraform을 초기화합니다.

```bash
terraform init
```

생성 계획을 확인합니다.

```bash
terraform plan
```

인프라를 생성합니다.

```bash
terraform apply
```

생성 후 `public_ip`, `hosted_zone_name_servers`, `shortlinkops_base_url`, `private_key_file`, `ssh_command`가 출력됩니다.

도메인 등록기관에서 `hosted_zone_name_servers` 값을 name server로 설정합니다.

## 접속

```bash
ssh -i generated/shortlinkops-key.pem ubuntu@<elastic-ip>
```

EC2 접속 후 루트 README의 Docker Compose 배포 절차를 실행합니다.

## 삭제

```bash
terraform destroy
```

## Git 제외 대상

다음 파일과 디렉토리는 Git에 포함하지 않습니다.

```text
terraform.tfvars
*.tfstate
*.tfstate.*
.terraform/
generated/
*.pem
```
