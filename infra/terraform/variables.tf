variable "aws_region" {
  description = "EC2 인스턴스를 생성할 AWS 리전입니다."
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "리소스 이름과 태그에 사용할 프로젝트 이름입니다."
  type        = string
  default     = "shortlinkops"
}

variable "environment" {
  description = "배포 환경 이름입니다."
  type        = string
  default     = "dev"
}

variable "domain_name" {
  description = "Route53 Hosted Zone과 루트 A 레코드에 사용할 도메인 이름입니다."
  type        = string
  default     = "fhwang.cloud"
}

variable "instance_type" {
  description = "EC2 인스턴스 유형입니다."
  type        = string
  default     = "t3.small"
}

variable "key_pair_name" {
  description = "Terraform이 생성할 EC2 키페어 이름입니다."
  type        = string
  default     = "shortlinkops-key"
}

variable "private_key_file" {
  description = "Terraform이 생성한 SSH private key를 저장할 로컬 파일 경로입니다. 이 파일은 Git에 포함하지 않습니다."
  type        = string
  default     = "generated/shortlinkops-key.pem"
}

variable "ssh_allowed_cidr" {
  description = "SSH 22번 포트 접근을 허용할 CIDR입니다. dev 기본값은 전체 IPv4 대역입니다."
  type        = string
  default     = "0.0.0.0/0"

  validation {
    condition     = can(cidrhost(var.ssh_allowed_cidr, 0))
    error_message = "ssh_allowed_cidr는 203.0.113.10/32 같은 올바른 CIDR 형식이어야 합니다."
  }
}

variable "root_volume_size" {
  description = "루트 EBS 볼륨 크기입니다."
  type        = number
  default     = 30
}

variable "certbot_email" {
  description = "Let's Encrypt 인증서 알림을 받을 이메일입니다. 빈 값이면 이메일 없이 인증서를 요청합니다."
  type        = string
  default     = ""
}

variable "tags" {
  description = "리소스에 추가로 적용할 태그입니다."
  type        = map(string)
  default     = {}
}
